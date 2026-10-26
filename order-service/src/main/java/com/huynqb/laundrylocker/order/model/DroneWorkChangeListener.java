package com.huynqb.laundrylocker.order.model;

import com.huynqb.laundrylocker.common.event.MaintenanceChangeEvents;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/// Báo app KTV drone mỗi khi việc điều phối/giao drone đổi — đơn drone (khách trả tiền, huỷ, chặng giao),
/// nhiệm vụ bay, lượt lấy hàng (KTV khác nhận đơn), sự cố kiện drone (bằng chứng, phương án khách chọn) —
/// để hàng điều phối và mục thu hồi kiện không phải chờ vòng tải lại định kỳ. Đơn tủ thường không báo.
@Component
public class DroneWorkChangeListener {

    private final ObjectProvider<RabbitTemplate> rabbitTemplate;

    public DroneWorkChangeListener(ObjectProvider<RabbitTemplate> rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostPersist
    @PostUpdate
    @PostRemove
    void changed(Object entity) {
        switch (entity) {
            case LockerOrder order when "DRONE_DELIVERY".equals(order.getType()) ->
                    publish(MaintenanceChangeEvents.DRONE_ORDER, order.getId());
            case DroneMission mission -> publish(MaintenanceChangeEvents.DRONE_ORDER, mission.getOrderId());
            case DronePickup pickup -> publish(MaintenanceChangeEvents.DRONE_ORDER, pickup.getOrderId());
            case DroneParcelIncident incident -> publish(MaintenanceChangeEvents.DRONE_INCIDENT, incident.getId());
            case DroneIncidentTimeline timeline ->
                    publish(MaintenanceChangeEvents.DRONE_INCIDENT, timeline.getIncidentId());
            case DroneIncidentResolutionProposal proposal ->
                    publish(MaintenanceChangeEvents.DRONE_INCIDENT, proposal.getIncidentId());
            case DroneIncidentEvidence evidence ->
                    publish(MaintenanceChangeEvents.DRONE_INCIDENT, evidence.getIncidentId());
            default -> {
            }
        }
    }

    private void publish(String entity, Long id) {
        MaintenanceChangeEvents.changed(rabbitTemplate.getIfAvailable(), "order-service", entity, id, null);
    }
}
