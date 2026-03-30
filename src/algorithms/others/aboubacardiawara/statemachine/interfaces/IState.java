package algorithms.others.aboubacardiawara.statemachine.interfaces;

import java.util.function.Supplier;
import algorithms.others.aboubacardiawara.statemachine.AnyTransitionConditionMetException;

public interface IState {
    boolean hasNext();

    IState next() throws AnyTransitionConditionMetException;

    void setUp(Runnable setUpAction);

    void tearDown(Runnable tearDownAction);

    void setUp();

    void tearDown();

    void addNext(IState state, Supplier<Boolean> transitionCondition);

    void addNext(IState state);

    void setStateAction(Runnable transitionAction);

    void performsAction();

    void setDescription(String tourneVersLeNord);

    String dotify();
}