package com.huynqb.laundrylocker.order.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(DroneReturnCommandGateway.class)
public class UnavailableDroneReturnCommandGateway implements DroneReturnCommandGateway {
    @Override
    public CommandResult requestReturnToLaunch(String droneCode, String incidentCode) {
        return new CommandResult(false, "RTL integration unavailable");
    }
}
