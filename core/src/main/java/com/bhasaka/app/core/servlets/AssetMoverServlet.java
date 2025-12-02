package com.bhasaka.app.core.servlets;

import com.bhasaka.app.core.utility.SystemUserServiceUtility;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.*;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.jcr.Session;
import java.io.IOException;

@Component(
        service = javax.servlet.Servlet.class,
        property = {
                "sling.servlet.paths=/bin/bhasaka/moveassets",
                "sling.servlet.methods=GET"
        }
)
public class AssetMoverServlet extends SlingAllMethodsServlet {

    @Reference
    private SystemUserServiceUtility systemUserServiceUtility;
    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws IOException {

        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().println("Asset Mover Servlet - Moving assets now...\n");

        String sourcePath = "/content/dam/sample";     // your source
        String targetPath = "/content/dam/app";    // your target

        try (ResourceResolver resolver = systemUserServiceUtility.getResourceResolver()) {

            Resource source = resolver.getResource(sourcePath);
            Resource target = resolver.getResource(targetPath);

            if (source == null || target == null) {
                response.getWriter().println("ERROR: Source or target folder not found!");
                return;
            }

            Session session = resolver.adaptTo(Session.class);
            int moved = 0;

            for (Resource child : source.getChildren()) {
                if ("dam:Asset".equals(child.getResourceType())) {
                    String destPath = targetPath + "/" + child.getName();

                    if (resolver.getResource(destPath) != null) {
                        response.getWriter().println("SKIP (exists): " + destPath);
                        continue;
                    }

                    session.move(child.getPath(), destPath);
                    moved++;
                    response.getWriter().println("MOVED: " + child.getPath() + " → " + destPath);
                } else {
                    response.getWriter().println("IGNORED: " + child.getPath() + " (type: " + child.getResourceType() + ")");
                }
            }

            resolver.commit();
            response.getWriter().println("\nSUCCESS! " + moved + " asset(s) moved successfully!");
            response.getWriter().println("View: http://localhost:4502/assets.html" + targetPath);

        } catch (Exception e) {
            response.getWriter().println("FAILED: " + e.getMessage());
            e.printStackTrace(response.getWriter());
        }
    }
}
