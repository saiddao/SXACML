package ontoplay.models.owlGeneration;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.util.Types;
import ontoplay.models.ConfigurationException;
import ontoplay.models.PropertyCondition;

import javax.inject.Inject;

/**
 * Created by michal on 30.11.2016.
 */
public class RestrictionFactoryProvider {

    private final Injector injector;

    @Inject
    public RestrictionFactoryProvider(Injector injector) {
        this.injector = injector;
    }

    public <U extends PropertyCondition> RestrictionFactory<U> getRestrictionFactory(U condition) throws ConfigurationException {
        //return (PropertyConditionRenderer<U>) injector.getInstance(Key.get(TypeLiteral.get(Types.newParameterizedType(PropertyConditionRenderer.class, property.getClass()))));
        return (RestrictionFactory<U>) injector.getInstance(Key.get(TypeLiteral.get(Types.newParameterizedType(RestrictionFactory.class, condition.getClass()))));
//        return injector.getInstance(Key.get(new TypeLiteral<RestrictionFactory<U>>(){}));
    }
}
