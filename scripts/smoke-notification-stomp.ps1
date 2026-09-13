param(
  [string]$BaseUrl = "http://localhost:8080",
  [Parameter(Mandatory = $true)]
  [string]$AccessToken,
  [int]$TimeoutSeconds = 10,
  [string]$SubscribeDestination,
  [int]$WaitForMessageSeconds = 0,
  [string]$ExpectedText,
  [string]$TriggerUrl,
  [string]$TriggerBearerToken,
  [string]$TriggerBodyJson,
  [int]$TriggerDelaySeconds = 1
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Convert-To-WebSocketUri {
  param([string]$HttpBaseUrl)

  $builder = [System.UriBuilder]::new($HttpBaseUrl)
  if ($builder.Scheme -eq "https") {
    $builder.Scheme = "wss"
  } else {
    $builder.Scheme = "ws"
  }

  $path = $builder.Path.TrimEnd("/")
  if ([string]::IsNullOrWhiteSpace($path)) {
    $builder.Path = "/ws"
  } elseif (-not $path.EndsWith("/ws")) {
    $builder.Path = "$path/ws"
  }
  return $builder.Uri
}

function Send-StompFrame {
  param(
    [System.Net.WebSockets.ClientWebSocket]$Client,
    [System.Threading.CancellationToken]$CancellationToken,
    [string]$Frame
  )

  $bytes = [System.Text.Encoding]::UTF8.GetBytes($Frame)
  $segment = [System.ArraySegment[byte]]::new($bytes)
  $null = $Client.SendAsync(
      $segment,
      [System.Net.WebSockets.WebSocketMessageType]::Text,
      $true,
      $CancellationToken).GetAwaiter().GetResult()
}

function Receive-StompFrame {
  param(
    [System.Net.WebSockets.ClientWebSocket]$Client,
    [System.Threading.CancellationToken]$CancellationToken
  )

  $buffer = [byte[]]::new(4096)
  $message = [System.Text.StringBuilder]::new()

  do {
    $segment = [System.ArraySegment[byte]]::new($buffer)
    $result = $Client.ReceiveAsync($segment, $CancellationToken).GetAwaiter().GetResult()
    if ($result.MessageType -eq [System.Net.WebSockets.WebSocketMessageType]::Close) {
      throw "WebSocket closed before STOMP CONNECT completed."
    }

    [void]$message.Append([System.Text.Encoding]::UTF8.GetString($buffer, 0, $result.Count))
    $current = $message.ToString()
  } until ($result.EndOfMessage -or $current.Contains("`0"))

  return $message.ToString().TrimEnd([char]0)
}

$wsUri = Convert-To-WebSocketUri -HttpBaseUrl $BaseUrl
$client = [System.Net.WebSockets.ClientWebSocket]::new()
$cts = [System.Threading.CancellationTokenSource]::new([System.TimeSpan]::FromSeconds($TimeoutSeconds))

try {
  $client.Options.SetRequestHeader("Authorization", "Bearer $AccessToken")
  $null = $client.ConnectAsync($wsUri, $cts.Token).GetAwaiter().GetResult()

  $connectFrame =
    "CONNECT`n" +
    "accept-version:1.2`n" +
    "heart-beat:0,0`n" +
    "Authorization:Bearer $AccessToken`n" +
    "`n" +
    "`0"
  Send-StompFrame -Client $client -CancellationToken $cts.Token -Frame $connectFrame

  $response = Receive-StompFrame -Client $client -CancellationToken $cts.Token
  if (-not $response.StartsWith("CONNECTED")) {
    throw "STOMP CONNECT did not succeed. Response: $response"
  }

  if ([string]::IsNullOrWhiteSpace($SubscribeDestination)) {
    Write-Host "PASS STOMP CONNECT authenticated through $wsUri"
    return
  }

  $subscribeFrame =
    "SUBSCRIBE`n" +
    "id:smoke-notifications`n" +
    "destination:$SubscribeDestination`n" +
    "`n" +
    "`0"
  Send-StompFrame -Client $client -CancellationToken $cts.Token -Frame $subscribeFrame

  if ($WaitForMessageSeconds -le 0) {
    Write-Host "PASS STOMP CONNECT authenticated and subscribed to $SubscribeDestination through $wsUri"
    return
  }

  if (-not [string]::IsNullOrWhiteSpace($TriggerUrl)) {
    Start-Sleep -Seconds $TriggerDelaySeconds
    $headers = @{}
    if (-not [string]::IsNullOrWhiteSpace($TriggerBearerToken)) {
      $headers["Authorization"] = "Bearer $TriggerBearerToken"
    }
    $triggerArgs = @{
      Method = "Post"
      Uri = $TriggerUrl
      Headers = $headers
      TimeoutSec = $TimeoutSeconds
    }
    if (-not [string]::IsNullOrWhiteSpace($TriggerBodyJson)) {
      $triggerArgs["ContentType"] = "application/json"
      $triggerArgs["Body"] = $TriggerBodyJson
    }
    $null = Invoke-RestMethod @triggerArgs
  }

  $messageCts =
    [System.Threading.CancellationTokenSource]::new(
        [System.TimeSpan]::FromSeconds($WaitForMessageSeconds))
  try {
    $message = Receive-StompFrame -Client $client -CancellationToken $messageCts.Token
  } finally {
    $messageCts.Dispose()
  }

  if (-not $message.StartsWith("MESSAGE")) {
    throw "Expected STOMP MESSAGE from $SubscribeDestination. Response: $message"
  }
  if (-not [string]::IsNullOrWhiteSpace($ExpectedText) -and -not $message.Contains($ExpectedText)) {
    throw "Received STOMP MESSAGE, but it did not contain expected text '$ExpectedText'."
  }

  Write-Host "PASS STOMP MESSAGE received from $SubscribeDestination through $wsUri"
} finally {
  if ($client.State -eq [System.Net.WebSockets.WebSocketState]::Open) {
    $null = $client.CloseAsync(
        [System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure,
        "smoke complete",
        [System.Threading.CancellationToken]::None).GetAwaiter().GetResult()
  }
  $client.Dispose()
  $cts.Dispose()
}
