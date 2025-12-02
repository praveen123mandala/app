package com.bhasaka.app.core.utility;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.HashMap;
import java.util.Map;

@Component(service = SystemUserServiceUtility.class, immediate = true)
public class SystemUserServiceUtility {

    @Reference
    ResourceResolverFactory factory;

    public ResourceResolver getResourceResolver(){
        ResourceResolver resolver=null;

        Map<String,Object> map=new HashMap<>();

        map.put(ResourceResolverFactory.SUBSERVICE,"asset-mover-service");
        try {
            resolver=factory.getServiceResourceResolver(map);
        } catch (LoginException e) {
            throw new RuntimeException(e);
        }

        return resolver;
    }
}

