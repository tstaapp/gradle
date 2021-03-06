/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.component.external.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.external.descriptor.Artifact;
import org.gradle.internal.component.local.model.DefaultProjectDependencyMetadata;
import org.gradle.internal.component.model.AttributeConfigurationSelector;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.IvyArtifactName;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public abstract class DefaultDependencyMetadata implements ModuleDependencyMetadata {
    private final List<Artifact> dependencyArtifacts;
    private final ModuleComponentSelector selector;
    private final boolean optional;

    protected DefaultDependencyMetadata(ModuleComponentSelector selector, List<Artifact> artifacts, boolean optional) {
        this.selector = selector;
        dependencyArtifacts = ImmutableList.copyOf(artifacts);
        this.optional = optional;
    }

    // TODO:DAZ Get rid of these: DefaultDependencyMetadata is not a _real_ `DependencyMetadata`
    @Override
    public Set<ConfigurationMetadata> selectConfigurations(ImmutableAttributes consumerAttributes, ComponentResolveMetadata targetComponent, AttributesSchemaInternal consumerSchema) {
        throw new UnsupportedOperationException("Work in progress: DefaultDependencyMetadata is not really a DependencyMetadata");
    }

    @Override
    public List<ExcludeMetadata> getExcludes() {
        throw new UnsupportedOperationException("Work in progress: DefaultDependencyMetadata is not really a DependencyMetadata");
    }

    @Override
    public Set<IvyArtifactName> getArtifacts() {
        throw new UnsupportedOperationException("Work in progress: DefaultDependencyMetadata is not really a DependencyMetadata");
    }

    @Override
    public DefaultDependencyMetadata withRequestedVersion(VersionConstraint requestedVersion) {
        if (requestedVersion.equals(selector.getVersionConstraint())) {
            return this;
        }
        ModuleComponentSelector newSelector = DefaultModuleComponentSelector.newSelector(selector.getGroup(), selector.getModule(), requestedVersion);
        return withRequested(newSelector);
    }

    @Override
    public DependencyMetadata withTarget(ComponentSelector target) {
        if (target instanceof ModuleComponentSelector) {
            ModuleComponentSelector moduleTarget = (ModuleComponentSelector) target;
            ModuleComponentSelector newSelector = DefaultModuleComponentSelector.newSelector(moduleTarget.getGroup(), moduleTarget.getModule(), moduleTarget.getVersionConstraint());
            if (newSelector.equals(selector)) {
                return this;
            }
            return withRequested(newSelector);
        } else if (target instanceof ProjectComponentSelector) {
            ProjectComponentSelector projectTarget = (ProjectComponentSelector) target;
            return new DefaultProjectDependencyMetadata(projectTarget, this);
        } else {
            throw new IllegalArgumentException("Unexpected selector provided: " + target);
        }
    }

    protected abstract DefaultDependencyMetadata withRequested(ModuleComponentSelector newRequested);

    @Override
    public ModuleComponentSelector getSelector() {
        return selector;
    }

    public List<Artifact> getDependencyArtifacts() {
        return dependencyArtifacts;
    }

    @Override
    public boolean isOptional() {
        return optional;
    }

    // TODO:DAZ Could do this when constructing the ConfigurationMetadata
    Set<IvyArtifactName> getConfigurationArtifacts(ConfigurationMetadata fromConfiguration) {
        if (dependencyArtifacts.isEmpty()) {
            return Collections.emptySet();
        }

        // TODO:DAZ This logic should be simpler for Maven dependencies: they can only set a single artifact via classifier/type.
        Collection<String> includedConfigurations = fromConfiguration.getHierarchy();
        Set<IvyArtifactName> artifacts = Sets.newLinkedHashSetWithExpectedSize(dependencyArtifacts.size());
        for (Artifact depArtifact : dependencyArtifacts) {
            Set<String> artifactConfigurations = depArtifact.getConfigurations();
            if (include(artifactConfigurations, includedConfigurations)) {
                IvyArtifactName ivyArtifactName = depArtifact.getArtifactName();
                artifacts.add(ivyArtifactName);
            }
        }
        return artifacts;
    }

    protected static boolean include(Iterable<String> configurations, Collection<String> acceptedConfigurations) {
        for (String configuration : configurations) {
            if (configuration.equals("*")) {
                return true;
            }
            if (acceptedConfigurations.contains(configuration)) {
                return true;
            }
        }
        return false;
    }

    public Set<ConfigurationMetadata> getMetadataForConfigurations(ImmutableAttributes consumerAttributes, AttributesSchemaInternal consumerSchema, ComponentIdentifier fromComponent, ConfigurationMetadata fromConfiguration, ComponentResolveMetadata targetComponent) {
        if (!targetComponent.getVariantsForGraphTraversal().isEmpty()) {
            // This condition shouldn't be here, and attribute matching should always be applied when the target has variants
            // however, the schemas and metadata implementations are not yet set up for this, so skip this unless:
            // - the consumer has asked for something specific (by providing attributes), as the other metadata types are broken for the 'use defaults' case
            // - or the target is a component from a Maven/Ivy repo as we can assume this is well behaved
            if (!consumerAttributes.isEmpty() || targetComponent instanceof ModuleComponentResolveMetadata) {
                return ImmutableSet.of(AttributeConfigurationSelector.selectConfigurationUsingAttributeMatching(consumerAttributes, targetComponent, consumerSchema));
            }
        }
        return selectLegacyConfigurations(fromComponent, fromConfiguration, targetComponent);
    }

    protected abstract Set<ConfigurationMetadata> selectLegacyConfigurations(ComponentIdentifier fromComponent, ConfigurationMetadata fromConfiguration, ComponentResolveMetadata targetComponent);

    public abstract List<ExcludeMetadata> getConfigurationExcludes(Collection<String> configurations);

    /**
     * Returns the set of source configurations that this dependency should be attached to.
     */
    public abstract Set<String> getModuleConfigurations();
}
