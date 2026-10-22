-- Luu dia chi IP cua Raspberry Pi khi Pi gui discovery len MQTT (ADR-0008)
ALTER TABLE iot_schema.gateway_devices ADD COLUMN IF NOT EXISTS ip_address VARCHAR(45);

