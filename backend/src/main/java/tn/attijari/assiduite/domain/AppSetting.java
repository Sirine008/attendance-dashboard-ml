package tn.attijari.assiduite.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "app_settings")
@Getter
@Setter
@NoArgsConstructor
public class AppSetting {
    @Id
    @Column(name = "setting_key")
    private String key;
    @Column(name = "setting_value")
    private String value;
    private String description;
}
