package com.huynqb.laundrylocker.order.it;

import com.huynqb.laundrylocker.order.client.DronePositionClient;
import com.huynqb.laundrylocker.order.client.LockerCellClient;
import com.huynqb.laundrylocker.order.client.LockerClient;
import com.huynqb.laundrylocker.order.client.LockerDroneClient;
import com.huynqb.laundrylocker.order.client.LockerLookupClient;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.client.PaymentRefundClient;
import com.huynqb.laundrylocker.order.client.PaymentReportClient;
import com.huynqb.laundrylocker.order.client.StoreLookupClient;
import com.huynqb.laundrylocker.order.client.UserClient;
import com.huynqb.laundrylocker.order.client.UserLookupClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/// Nền cho test tích hợp order-service trên Postgres THẬT: Flyway chạy đủ các migration, Hibernate
/// `validate` soát entity với schema, và mọi câu truy vấn chạm database thật. Chỉ các service khác
/// (Feign) và RabbitMQ là giả — đó là ranh giới của order-service.
///
/// Container dùng chung cho mọi lớp test kế thừa (khởi động một lần mỗi JVM); mỗi test tự dọn
/// dữ liệu của mình. Máy không có Docker thì các test này tự bỏ qua.
@SpringBootTest(properties = {
        // JpaSliceTestConfig (cấu hình của test JPA dùng H2) nằm trong gói được quét và khai báo lại các
        // repository; ở context đầy đủ này cho phép định nghĩa trùng thay vì sửa test của luồng khác.
        "spring.main.allow-bean-definition-overriding=true",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.cloud.openfeign.circuitbreaker.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        // Job nền không được chen vào giữa các bước của test; test tự gọi khi cần.
        "app.order.drone-timeout-sweep-cron=-",
        "app.order.drone-pickup-sweep-cron=-",
        "app.order.drone-block-sweep-cron=-",
        "app.order.auto-cancel-cron=-",
        "app.order.release-sweep-cron=-",
        "app.order.overdue-release-cron=-",
        "app.order.reconcile-cron=-",
        "app.order.reminder-interval-ms=86400000",
        "app.drone.demo.scheduler-delay-ms=86400000",
        "app.drone.telemetry.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
public abstract class OrderServicePostgresTestBase {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("order_db")
            .withUsername("order_user")
            .withPassword("order_pass");

    static {
        // Khởi động tay (không dùng @Container) để mọi lớp test dùng chung một database.
        if (org.testcontainers.DockerClientFactory.instance().isDockerAvailable()) {
            POSTGRES.start();
        }
    }

    @MockitoBean
    protected UserClient userClient;
    @MockitoBean
    protected UserLookupClient userLookupClient;
    @MockitoBean
    protected LockerClient lockerClient;
    @MockitoBean
    protected LockerCellClient lockerCellClient;
    @MockitoBean
    protected LockerLookupClient lockerLookupClient;
    @MockitoBean
    protected LockerDroneClient lockerDroneClient;
    @MockitoBean
    protected NotificationClient notificationClient;
    @MockitoBean
    protected PaymentRefundClient paymentRefundClient;
    @MockitoBean
    protected PaymentReportClient paymentReportClient;
    @MockitoBean
    protected StoreLookupClient storeLookupClient;
    @MockitoBean
    protected DronePositionClient dronePositionClient;
    @MockitoBean
    protected RabbitTemplate rabbitTemplate;
}
