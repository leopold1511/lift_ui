/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Enum.java to edit this template
 */
package ru.java_inside.lift_ui.lift;

import lombok.Getter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Граф переходов между действиями
 *
 * @author 6PATyCb
 */
@Getter
public enum LiftActionsGraph {
    /**
     * Пустой лифт ничего не делает
     */
    ON_NO_ACTION_WHEN_EMPTY(LiftAction.NO_ACTION_WHEN_EMPTY, PassengerAction.CALL, PassengerAction.STEP_ON),
    /**
     * Лифт с пассажиром ничего не делает
     */
    ON_NO_ACTION_WITH_PASSANGER(LiftAction.NO_ACTION_WITH_PASSANGER, PassengerAction.GO_TO_FLOOR),
    /**
     * Порожнее движение на этаж
     */
    ON_MOVE_TO_FLOOR_WHEN_EMPTY(LiftAction.MOVE_TO_FLOOR_WHEN_EMPTY),
    /**
     * движение на этаж c пассажиром
     */
    ON_MOVE_TO_FLOOR_WITH_PASSANGER(LiftAction.MOVE_TO_FLOOR_WITH_PASSANGER),
    /**
     * когда лифт сломан
     */
    ON_BROKEN(LiftAction.BROKEN, PassengerAction.REPAIR);

    private final LiftAction currentAction;
    private final PassengerAction[] availableActions;

    private LiftActionsGraph(LiftAction currentAction, PassengerAction... availableActions) {
        this.currentAction = currentAction;
        this.availableActions = availableActions;
    }

    /**
     * Получение доступных действий относительно текущего действия
     *
     * @param currentAction
     * @return
     */
    public static Set<PassengerAction> getAvailableActions(LiftAction currentAction) {
        Set<PassengerAction> availableActions = new HashSet<>();
        for (LiftActionsGraph graphValue : values()) {
            if (currentAction == graphValue.getCurrentAction()) {
                availableActions.addAll(Arrays.asList(graphValue.getAvailableActions()));
                break;
            }
        }
        return availableActions;
    }

}
