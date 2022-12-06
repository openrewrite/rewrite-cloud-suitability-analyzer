package org.openrewrite.cloudsuitability;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import static org.openrewrite.test.SourceSpecs.text;

public class FindUnsuitableCodeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
                .scanRuntimeClasspath("org.openrewrite.cloudsuitability")
                .build()
                .activateRecipes("org.openrewrite.cloudsuitability.FindUnsuitableCode"));
    }

    @Test
    void reportUseOfKeyStore() {
        rewriteRun(
                text("contents do not matter", "~~>contents do not matter", spec -> spec.path("mykeystore.jks"))
        );

    }
}
