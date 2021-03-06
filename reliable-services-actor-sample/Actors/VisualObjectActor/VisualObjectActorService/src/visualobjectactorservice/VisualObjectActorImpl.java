// ------------------------------------------------------------
//  Copyright (c) Microsoft Corporation.  All rights reserved.
//  Licensed under the MIT License (MIT). See License.txt in the repo root for license information.
// ------------------------------------------------------------

package visualobjectactorservice;

import java.time.Duration;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

import microsoft.servicefabric.actors.runtime.ActorServiceAttribute;
import microsoft.servicefabric.actors.runtime.ActorTimer;
import microsoft.servicefabric.actors.runtime.FabricActor;
import microsoft.servicefabric.actors.runtime.StatePersistence;
import microsoft.servicefabric.actors.runtime.StatePersistenceAttribute;
import system.fabric.ConfigurationPackage;
import system.fabric.description.ConfigurationProperty;
import system.fabric.description.ConfigurationSection;
import system.fabric.description.ConfigurationSettings;
import visualobjectcommon.VisualObject;
import visualobjectcommon.VisualObjectActor;
import microsoft.servicefabric.actors.ActorId;
import microsoft.servicefabric.actors.runtime.FabricActorService;

@ActorServiceAttribute(name = "VisualObjects.ActorService")
@StatePersistenceAttribute(statePersistence = StatePersistence.Persisted)
public class VisualObjectActorImpl extends FabricActor implements VisualObjectActor {

    private String jsonString;
    private final String stateName = "visualObjects";    

    public VisualObjectActorImpl(FabricActorService actorService, ActorId actorId){
        super(actorService, actorId);
    }
    
    @Override
    public CompletableFuture<String> getStateAsJsonAsync() {
        CompletableFuture<String> result = new CompletableFuture<String>();
        result.complete(this.jsonString);
        return result;
    }

    protected CompletableFuture<?> onActivateAsync() {
        long timerIntervalInMilliSeconds = this.getTimerIntervalFromSettings();
        return this.stateManager()
                .getOrAddStateAsync(
                        stateName,
                        VisualObject.createRandom(
                                this.getId().toString(),
                                new Random(this.getId().toString().hashCode())))
                .thenApply((r) -> {
                    this.jsonString = r.toJson();
                    this.registerTimer(
                            (o) -> this.moveObject(o),
                            "moveObject",
                            null,
                            Duration.ofMillis(10),
                            Duration.ofMillis(timerIntervalInMilliSeconds));
                    return null;
                });
    }

    private long getTimerIntervalFromSettings() {
        ConfigurationPackage configPackage = this.getActorService().getServiceContext().getCodePackageActivationContext().getConfigurationPackageObject("Config");
        ConfigurationSettings settings = null;
        if (configPackage != null)
            settings = configPackage.getSettings();
        HashMap<String, ConfigurationSection> sections = null;
        if (settings != null) {
            sections = settings.getSections();
        }

        ConfigurationSection serviceSection = null;
        if (sections != null) {
            serviceSection = sections.get("VisualObjects.TimerFrequency");
        }
        
        HashMap<String, ConfigurationProperty> properties = null;
        if (serviceSection != null) {
            properties = serviceSection.getParameters();
        }
        
        String timerInterval = properties.get("TimerInvocationIntervalInMilliseconds").getValue();
        return Long.parseLong(timerInterval);
    }

    private CompletableFuture<?> moveObject(Object state) {
        return this.stateManager().getStateAsync(this.stateName).thenCompose(v -> {
            VisualObject v1 = (VisualObject)v;
            v1.move();
            return (CompletableFuture<?>)this.stateManager().setStateAsync(stateName, v1).
                    thenApply(r -> {this.jsonString = v1.toJson(); return null;});
        });
     }
}
