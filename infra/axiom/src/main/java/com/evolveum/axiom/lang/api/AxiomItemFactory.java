package com.evolveum.axiom.lang.api;

import java.util.Collection;

public interface AxiomItemFactory<V> {

    AxiomItem<V> create(AxiomItemDefinition def, Collection<? extends AxiomItemValue<?>> axiomItem);

}