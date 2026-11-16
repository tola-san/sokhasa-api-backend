package com.sokhasaclinic.clinicbackend.config;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Controller serving the OpenAPI specification and Scalar interactive API documentation.
 */
@Controller
public class ScalarDocController {

    private final Resource openApiSpec = new ClassPathResource("openapi.yaml");

    @GetMapping(value = "/api/v1/openapi.yaml", produces = "application/yaml")
    @ResponseBody
    public Resource getOpenApiYaml() {
        return openApiSpec;
    }

    @GetMapping(value = {"/scalar", "/docs", "/api/v1/docs"}, produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String getScalarUi() {
        return """
            <!doctype html>
            <html lang="en">
              <head>
                <title>Sokhasa Clinic API Reference</title>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1" />
                <link rel="icon" type="image/svg+xml" href="data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 100 100'><text y='.9em' font-size='90'>🏥</text></svg>" />
                <style>
                  body {
                    margin: 0;
                    padding: 0;
                    height: 100vh;
                  }
                </style>
              </head>
              <body>
                <script
                  id="api-reference"
                  data-url="/api/v1/openapi.yaml"
                  data-configuration='{"theme":"purple","darkMode":true,"layout":"modern","showSidebar":true,"searchHotKey":"k"}'>
                </script>
                <script src="https://cdn.jsdelivr.net/npm/@scalar/api-reference"></script>
              </body>
            </html>
            """;
    }
}
