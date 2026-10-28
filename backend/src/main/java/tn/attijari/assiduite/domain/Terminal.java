package tn.attijari.assiduite.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "terminal")
@Getter
@Setter
@NoArgsConstructor
public class Terminal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String code;
    private String label;
    @Column(name = "is_building_boundary")
    private boolean buildingBoundary;
}
