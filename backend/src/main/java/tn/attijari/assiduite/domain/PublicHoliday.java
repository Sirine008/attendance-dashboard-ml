package tn.attijari.assiduite.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "public_holiday")
@Getter
@Setter
@NoArgsConstructor
public class PublicHoliday {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private LocalDate holidayDate;
    private String label;
    @Column(name = "is_religious")
    private boolean religious;
    private boolean toVerify;
}
