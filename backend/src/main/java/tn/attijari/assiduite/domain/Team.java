package tn.attijari.assiduite.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "team")
@Getter
@Setter
@NoArgsConstructor
public class Team {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    @Column(name = "is_synthetic")
    private boolean synthetic;

    public Team(String name, boolean synthetic) {
        this.name = name;
        this.synthetic = synthetic;
    }
}
