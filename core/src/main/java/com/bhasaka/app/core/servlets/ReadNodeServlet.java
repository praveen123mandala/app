package com.bhasaka.app.core.servlets;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.api.resource.*;
import org.apache.sling.servlets.annotations.SlingServletPaths;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.jcr.Session;
import javax.servlet.Servlet;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component(
        service = Servlet.class,
        property = {
                Constants.SERVICE_DESCRIPTION + "=Read Node Properties Servlet",
                "sling.servlet.methods=" + HttpConstants.METHOD_GET
        }
)
@SlingServletPaths("/bin/readnode")
public class ReadNodeServlet extends SlingAllMethodsServlet {

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String path = request.getParameter("path");

        if (path == null || path.isEmpty()) {
            response.getWriter().write("{\"error\":\"Please provide ?path=/content/...\"}");
            return;
        }

        // ----- Get Resolver Using Service User -----
        Map<String, Object> params = new HashMap<>();
        params.put(ResourceResolverFactory.SUBSERVICE, "node-reader");

        try (ResourceResolver resolver = resolverFactory.getServiceResourceResolver(params)) {

            Resource resource = resolver.getResource(path);

            if (resource == null) {
                response.getWriter().write("{\"error\": \"Node not found\"}");
                return;
            }

            ValueMap properties = resource.getValueMap();

            String title = properties.get("jcr:title", String.class);
            String modifiedBy = properties.get("jcr:lastModifiedBy", String.class);

            // Build JSON output
            String json = "{"
                    + "\"title\":\"" + (title != null ? title : "") + "\","
                    + "\"lastModifiedBy\":\"" + (modifiedBy != null ? modifiedBy : "") + "\""
                    + "}";

            response.getWriter().write(json);

        } catch (LoginException e) {
            response.getWriter().write("{\"error\":\"Service user login failed\"}");
        }
    }
}

