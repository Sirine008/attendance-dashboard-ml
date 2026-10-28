package tn.attijari.assiduite;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SmokeTest {

    @Test
    void applicationClassIsLoadable() {
        assertThat(AssiduiteApplication.class).isNotNull();
    }
}
