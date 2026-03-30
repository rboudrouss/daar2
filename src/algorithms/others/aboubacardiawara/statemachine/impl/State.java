package algorithms.others.aboubacardiawara.statemachine.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import algorithms.others.aboubacardiawara.statemachine.AnyTransitionConditionMetException;
import algorithms.others.aboubacardiawara.statemachine.interfaces.IState;

public class State implements IState {

    protected List<IState> nextStates;
    protected List<Supplier<Boolean>> transitionConditions;
    protected Optional<Runnable> transitionAction;
    protected int cpt = 0;
    protected String description;
    protected Optional<Runnable> setUpAction = Optional.empty();
    protected Optional<Runnable> tearDownAction = Optional.empty();

    public State(int stateCount) {
        this.nextStates = new ArrayList<>();
        this.transitionConditions = new ArrayList<>();
        this.transitionAction = Optional.empty();
    }

    public State() {
        this(1);
        this.description = "";
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public boolean hasNext() {
        for (int i = 0; i < nextStates.size(); i++) {
            if (transitionConditions.get(i).get()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public IState next() throws AnyTransitionConditionMetException {

        for (int i = 0; i < transitionConditions.size(); i++) {
            if (transitionConditions.get(i).get()) {
                return nextStates.get(i);
            }
        }
        throw new AnyTransitionConditionMetException();
    }

    @Override
    public void setUp() {
        setUpAction.ifPresent(Runnable::run);
    }

    @Override
    public void addNext(IState state, Supplier<Boolean> transitionCondition) {
        if (state != null) {
            this.nextStates.add(state);
            this.transitionConditions.add(transitionCondition);
        }
    }

    @Override
    public void addNext(IState state) {
        this.addNext(state, () -> true);
    }

    @Override
    public void setStateAction(Runnable transitionAction) {
        if (transitionAction != null)
            this.transitionAction = Optional.of(transitionAction);
    }

    @Override
    public void performsAction() {
        this.transitionAction.ifPresent(Runnable::run);
    }

    @Override
    public String toString() {
        return this.description;
    }

    @Override
    public String dotify() {
        return "digraph G {\n" + dotifyAux(this, new java.util.HashSet<>()) + "}";
    }

    public String dotifyAux(State state, Set<State> visited) {
        return "SHOULD BE IMPLEMENTED !!!";
    }

    @Override
    public void setUp(Runnable setUpAction) {
        this.setUpAction = Optional.of(setUpAction);

    }

    @Override
    public void tearDown(Runnable tearDownAction) {
        this.tearDownAction = Optional.of(tearDownAction);
    }

    @Override
    public void tearDown() {
        tearDownAction.ifPresent(Runnable::run);
    }
}