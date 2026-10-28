package tn.attijari.assiduite.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "import_batch")
@Getter
@Setter
@NoArgsConstructor
public class ImportBatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String sourceFile;
    @Column(name = "file_sha256")
    private String fileSha256;
    private String status;
    private int rowsRead;
    private int rowsInserted;
    private int rowsAlreadyKnown;
    private int rowsRejected;
    private int nearDuplicates;
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> report;
    private String triggeredBy;
    private Instant startedAt = Instant.now();
    private Instant finishedAt;
}
