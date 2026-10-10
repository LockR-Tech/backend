package com.huynqb.laundrylocker.order.service;

/** Physical-flight boundary. A request is not proof that the drone returned. */
public interface DroneReturnCommandGateway {
    CommandResult requestReturnToLaunch(String droneCode, String incidentCode);

    record CommandResult(boolean requested, String detail) {
    }
}

