/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.sling.feature.resolver;

import org.apache.sling.feature.Application;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.builder.ApplicationBuilder;
import org.apache.sling.feature.builder.BuilderContext;
import org.apache.sling.feature.builder.FeatureProvider;
import org.apache.sling.feature.io.ArtifactManager;
import org.apache.sling.feature.io.IOUtils;
import org.apache.sling.feature.io.file.ArtifactHandler;
import org.apache.sling.feature.io.json.FeatureJSONReader;
import org.apache.sling.feature.io.json.FeatureJSONReader.SubstituteVariables;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ApplicationResolverAssembler {
    /**
     * Assemble an application based on the given files.
     *
     * Read the features and assemble the application
     * @param app The optional application to use as a base.
     * @param featureFiles The feature files.
     * @param artifactManager The artifact manager
     * @param fr
     * @return The assembled application
     * @throws IOException If a feature can't be read or no feature is found.
     */
    public static Application assembleApplication(
            Application app,
            final ArtifactManager artifactManager,
            final FeatureResolver fr,
            final String... featureFiles)
    throws IOException {
        final List<Feature> features = new ArrayList<>();
        for(final String initFile : featureFiles) {
            try {
                final Feature f = IOUtils.getFeature(initFile, artifactManager, SubstituteVariables.RESOLVE);
                features.add(f);
            } catch (Exception ex) {
                throw new IOException("Error reading feature: " + initFile, ex);
            }
        }

        return assembleApplication(app, artifactManager, fr, features.toArray(new Feature[0]));
    }

    public static Feature[] sortFeatures(final FeatureResolver fr,
            final Feature... features) {
        final List<Feature> featureList = new ArrayList<>();
        for(final Feature f : features) {
            featureList.add(f);
        }

        final List<Feature> sortedFeatures;
        if (fr != null) {
            // order by dependency chain
            final List<FeatureResource> sortedResources = fr.orderResources(featureList);

            sortedFeatures = new ArrayList<>();
            for (final FeatureResource rsrc : sortedResources) {
                Feature f = rsrc.getFeature();
                if (f != null && !sortedFeatures.contains(f)) {
                    sortedFeatures.add(rsrc.getFeature());
                }
            }
        } else {
            sortedFeatures = featureList;
            Collections.sort(sortedFeatures);
        }
        return sortedFeatures.toArray(new Feature[sortedFeatures.size()]);
    }

    public static Application assembleApplication(
            Application app,
            final ArtifactManager artifactManager,
            final FeatureResolver fr,
            final Feature... features)
    throws IOException {
        if ( features.length == 0 ) {
            throw new IOException("No features found.");
        }

        app = ApplicationBuilder.assemble(app, new BuilderContext(new FeatureProvider() {

            @Override
            public Feature provide(final ArtifactId id) {
                try {
                    final ArtifactHandler handler = artifactManager.getArtifactHandler("mvn:" + id.toMvnPath());
                    try (final FileReader r = new FileReader(handler.getFile())) {
                        final Feature f = FeatureJSONReader.read(r, handler.getUrl(), SubstituteVariables.RESOLVE);
                        return f;
                    }

                } catch (final IOException e) {
                    // ignore
                }
                return null;
            }
        }), sortFeatures(fr, features));

        // check framework
        if ( app.getFramework() == null ) {
            // use hard coded Apache Felix
            app.setFramework(IOUtils.getFelixFrameworkId(null));
        }

        return app;
    }
}
