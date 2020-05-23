package com.evolveum.axiom.lang.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.evolveum.axiom.api.AxiomIdentifier;
import com.evolveum.axiom.lang.antlr.AxiomModelStatementSource;
import com.evolveum.axiom.lang.api.AxiomItemDefinition;
import com.evolveum.axiom.lang.api.AxiomItemValue;
import com.evolveum.axiom.lang.api.AxiomItemValueFactory;
import com.evolveum.axiom.lang.api.AxiomTypeDefinition;
import com.evolveum.axiom.lang.api.IdentifierSpaceKey;
import com.evolveum.axiom.lang.api.AxiomIdentifierDefinition.Scope;
import com.evolveum.axiom.lang.spi.AxiomItemStreamTreeBuilder;
import com.evolveum.axiom.lang.spi.SourceLocation;
import com.evolveum.axiom.reactor.Dependency;
import com.google.common.base.Preconditions;

class SourceContext extends ValueContext<Void> implements AxiomRootContext, AxiomItemStreamTreeBuilder.ValueBuilder {

    private static final AxiomIdentifier ROOT = AxiomIdentifier.from("root", "root");

    private final ModelReactorContext context;
    private final AxiomModelStatementSource source;
    private final Map<AxiomIdentifier, ItemContext> items = new HashMap();
    private final CompositeIdentifierSpace globalSpace;

    public SourceContext(ModelReactorContext context, AxiomModelStatementSource source, CompositeIdentifierSpace space) {
        super(SourceLocation.runtime(), space);
        this.context = context;
        this.source = source;
        this.globalSpace = space;
    }

    @Override
    public AxiomIdentifier name() {
        return ROOT;
    }

    @Override
    public ItemContext<?> startItem(AxiomIdentifier identifier, SourceLocation loc) {
        return items.computeIfAbsent(identifier, id -> createItem(id, loc));
    }

    @Override
    public Optional<AxiomItemDefinition> childDef(AxiomIdentifier statement) {
        return context.rootDefinition(statement);
    }

    @Override
    protected SourceContext rootImpl() {
        return this;
    }

    @Override
    public void endValue(SourceLocation loc) {
        // NOOP
    }

    @Override
    public void register(AxiomIdentifier space, Scope scope, IdentifierSpaceKey key, ValueContext<?> context) {
        globalSpace.register(space, scope, key, context);
        this.context.register(space, scope, key, context);
    }

    @Override
    public ValueContext<?> lookup(AxiomIdentifier space, IdentifierSpaceKey key) {
        return globalSpace.lookup(space, key);
    }

    public <V> AxiomItemValueFactory<V, AxiomItemValue<V>> factoryFor(AxiomTypeDefinition type) {
        return context.typeFactory(type);
    }

    public void applyRuleDefinitions(ValueContext<?> valueContext) {
        context.applyRuleDefinitions(valueContext);
    }

    public Dependency<NamespaceContext> requireNamespace(AxiomIdentifier name, IdentifierSpaceKey namespaceId) {
        return Dependency.retriableDelegate(() -> {
            return context.namespace(name, namespaceId);
        });
    }

    @Override
    public void importIdentifierSpace(NamespaceContext namespaceContext) {
        Preconditions.checkArgument(namespaceContext instanceof IdentifierSpaceHolder);
        globalSpace.add((IdentifierSpaceHolder) namespaceContext);
    }

    @Override
    public void exportIdentifierSpace(IdentifierSpaceKey namespaceId) {
        context.exportIdentifierSpace(namespaceId, globalSpace.getExport());
    }
}