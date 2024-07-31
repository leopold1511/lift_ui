/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package ru.java_inside.lift_ui.lift;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.java_inside.lift_ui.LiftUiException;
import ru.java_inside.lift_ui.lift.lift_ride.LiftRideEvent;
import ru.java_inside.lift_ui.users.Role;
import ru.java_inside.lift_ui.users.User;

/**
 * Отвечает за внутреннее поведение лифта
 *
 * @author 6PATyCb
 */
@Slf4j
@Component
public class LiftEngine {

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    /**
     * Максимальное время нахождения в лифте без указания этажа для поездки, сек
     */
    private int maxPassengerTimeout;
    /**
     *
     */
    private double breakChance;

    private Clock clock;

    /**
     * текущий этаж
     */
    private byte currentFloor = 1;
    /**
     * этаж последней остановки
     */
    private byte lastHoldFloor = currentFloor;
    /**
     * Этаж, где ожидают лифт
     */
    private byte waitFloor = currentFloor;
    /**
     * есть ли пассажир внутри
     */
    private Passenger passenger;
    /**
     * Лифт сломан
     */
    private boolean broken = false;
    /**
     * Действие лифта перед поломкой
     */
    private LiftAction beforeBreakAction;
    /**
     * Последнее текстовое представление состояния лифта
     */
    private String lastStateMessage = "";
    /**
     * Текущее действие лифта
     */
    private LiftAction currentAction = LiftAction.NO_ACTION_WHEN_EMPTY;
    /**
     * Открыта ли дверь
     */
    private boolean isOpen = false;
    /**
     * Время до автоматического закрытия двери
     */
    private long doorTimeOut = 7;
    /*
     * Время последнего открытия двери
     */
    private LocalDateTime doorOpeningTime;

    /**
     * Вычисляет следующее состояние лифта
     */
    @Scheduled(fixedDelay = 1000)
    public synchronized void updateLiftState() {
        {//когда лифт сломался
            if (broken) {
                lastStateMessage = String.format("%s сломан на %d этаже", getLiftTextPrefix(), currentFloor);
                log.info(lastStateMessage);
                return;
            }

        }
        {//когда мы находимся на нужном этаже
            if (currentFloor == waitFloor) {
                if (passenger != null) {
                    if (passenger.getToFloor() != null && waitFloor == passenger.getToFloor()) {
                        if(!isOpen) openDoor();
                        currentAction = LiftAction.NO_ACTION_WITH_PASSANGER;
                        int ridedFloors = passenger.getRidedFloors();
                        lastStateMessage = String.format("Пассажир %s вышел из лифта на %d этаже, проехав %d этажей", passenger.getUser(), currentFloor, ridedFloors);
                        LiftRideEvent event = new LiftRideEvent(ridedFloors, passenger.getUser(), this, clock);
                        passenger = null;
                        log.info(lastStateMessage);
                        applicationEventPublisher.publishEvent(event);
                        return;
                    }
                    if (passenger.isTimeToLeave(maxPassengerTimeout, LocalDateTime.now(clock))) {
                        currentAction = LiftAction.NO_ACTION_WITH_PASSANGER;
                        lastStateMessage = String.format("Пассажир %s вышел из лифта из-за неактивности на %d этаже", passenger.getUser(), currentFloor);
                        openDoor();
                        passenger = null;
                        log.info(lastStateMessage);
                        return;
                    }

                }
                lastHoldFloor = currentFloor;
                currentAction = passenger != null ? LiftAction.NO_ACTION_WITH_PASSANGER : LiftAction.NO_ACTION_WHEN_EMPTY;
                if (doorOpeningTime != null && Duration.between(doorOpeningTime, LocalDateTime.now(clock)).toSeconds() >= doorTimeOut) {
                    closeDoor();
                    doorOpeningTime = null;
                }
                String doorState = isOpen ? "Открытый" : "Закрытый";
                lastStateMessage = String.format("%s %s ничего не делает на %d этаже", doorState, getLiftTextPrefix(), currentFloor);
                log.info(lastStateMessage);
                return;
            }
        }
        {//движение на этаж
            breakChance();//вероятность сломаться
            if (broken) {//
                lastStateMessage = String.format("%s сломался на %d этаже", getLiftTextPrefix(), currentFloor);
                log.info(lastStateMessage);
                return;
            }
            if (currentFloor < waitFloor) {//движение вверх
                currentFloor++;
                currentAction = passenger != null ? LiftAction.MOVE_TO_FLOOR_WITH_PASSANGER : LiftAction.MOVE_TO_FLOOR_WHEN_EMPTY;
                lastStateMessage = String.format("%s переместился вверх на %d этаж", getLiftTextPrefix(), currentFloor);
                log.info(lastStateMessage);
                if (currentFloor == waitFloor) openDoor();
            } else if (currentFloor > waitFloor) {//движение вниз
                currentFloor--;
                currentAction = passenger != null ? LiftAction.MOVE_TO_FLOOR_WITH_PASSANGER : LiftAction.MOVE_TO_FLOOR_WHEN_EMPTY;
                lastStateMessage = String.format("%s переместился вниз на %d этаж", getLiftTextPrefix(), currentFloor);
                log.info(lastStateMessage);
                if (currentFloor == waitFloor) openDoor();
            }
            //     return;
        }
    }

    private String getLiftTextPrefix() {
        return passenger != null ? String.format("Лифт с пассажиром %s ", passenger.getUser()) : "пустой лифт";
    }

    /**
     * Вероятность сломать лифт
     */
    private void breakChance() {
        if (broken) {
            return;
        }
        broken = Math.random() < breakChance;
        if (broken) {
            beforeBreakAction = currentAction;
            currentAction = LiftAction.BROKEN;
        }
    }

    /**
     * Вызов лифта на этаж
     *
     * @param floor этаж
     */
    public synchronized void call(byte floor) {
        validateFloor(floor);
        waitFloor = floor;
        if (waitFloor == currentFloor) {
            openDoor();
            return;
        }
        lastStateMessage = String.format("Лифт вызван на %d этаж", currentFloor);
        log.info(lastStateMessage);
    }

    private void validateFloor(byte floor) {
        if (floor < 0) {
            throw new LiftUiException("Указан некорректный этаж");
        }
    }

    /**
     * Получение текущего состояния лифта
     *
     * @return
     */
    public synchronized LiftState getCurrentLiftState() {
        return new LiftState(currentFloor, lastHoldFloor, waitFloor, passenger != null ? passenger.getUser() : null, lastStateMessage, currentAction, broken, isOpen);
    }

    /**
     * Войти в лифт
     *
     * @param user
     */
    public synchronized void stepOn(User user) {
        if (user == null) {
            throw new IllegalArgumentException("Пользователь лифта не может быть null");
        }
        if (currentFloor != waitFloor) {
            throw new IllegalStateException("Нельзя войти в лифт когда он не находится на текущем этаже");
        }
        if (passenger != null) {
            throw new IllegalStateException("Нельзя войти в лифт когда он уже заполнен");
        }
        if (!isOpen) {
            throw new IllegalStateException("Нельзя войти в лифт когда дверь закрыта");
        }
        passenger = new Passenger(currentFloor, null, user, LocalDateTime.now(clock));
        lastStateMessage = String.format("Пассажир %s вошел в лифт", passenger.getUser());
        log.info(lastStateMessage);
    }

    /**
     * Поездка на этаж
     *
     * @param user
     * @param floor
     */
    public synchronized void goToFloor(User user, byte floor) {
        if (user == null) {
            throw new IllegalArgumentException("Пользователь лифта не может быть null");
        }
        validateFloor(floor);
        if (passenger == null) {
            throw new IllegalStateException("Нельзя ехать на этаж не зайдя в лифт");
        }
        if (!passenger.getUser().equals(user)) {
            throw new IllegalStateException("Текущий пользователь не является пассажиром этого лифта");
        }
        if (currentFloor == floor) {
            throw new IllegalStateException("Мы и так уже находимся на этом этаже");
        }
        passenger.setToFloor(floor);
        waitFloor = floor;
        if (isOpen)
            closeDoor();
        lastStateMessage = String.format("Пассажир %s в лифте нажал на копку %d", passenger.getUser(), floor);
        log.info(lastStateMessage);
    }

    /**
     * Починить лифт
     *
     * @param user
     */
    public synchronized void repair(User user) {
        if (user == null) {
            throw new IllegalArgumentException("Пользователь лифта не может быть null");
        }
        if (user.getRole() != Role.LIFT_ENGENEER) {
            throw new IllegalStateException(String.format("Лифт может чинить только '%s'", Role.LIFT_ENGENEER.getName()));
        }
        if (passenger != null && passenger.getUser().equals(user) && passenger.getUser().getRole() != Role.LIFT_ENGENEER) {
            throw new IllegalStateException("Вы не можете получить профессию лифтера, находясь в лифте");
        }
        currentAction = beforeBreakAction;
        broken = false;
        lastStateMessage = String.format("Лифтер %s починил лифт", user);
        log.info(lastStateMessage);
    }

    public synchronized void openDoor() {
        if (isOpen) {
            throw new IllegalStateException("Нельзя открыть открытую дверь");
        }
        doorOpeningTime = LocalDateTime.now(clock);
        isOpen = true;
        lastStateMessage = "Дверь открыта";
        log.info(lastStateMessage);
    }

    public synchronized void closeDoor() {
        if (!isOpen) {
            throw new IllegalStateException("Нельзя закрыть закрытую дверь");
        }
        isOpen = false;
        lastStateMessage = "Дверь закрыта";
        log.info(lastStateMessage);
    }

    @Value("${lift.maxPassengerTimeout:15}")
    public synchronized void setMaxPassengerTimeout(int maxPassengerTimeout) {
        //    System.out.println("!!!" + maxPassengerTimeout);
        this.maxPassengerTimeout = maxPassengerTimeout;
    }

    @Value("${lift.break_chance:0.1}")
    public synchronized void setBreakChance(double breakChance) {
        //    System.out.println("!!!" + breakChance);
        this.breakChance = breakChance;
    }

    @Autowired
    public synchronized void setClock(Clock clock) {
        this.clock = clock;
    }

}
