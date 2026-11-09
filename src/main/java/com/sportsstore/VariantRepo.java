package com.sportsstore;
import jakarta.persistence.LockModeType; import java.util.Optional;
import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param;
public interface VariantRepo extends JpaRepository<Variant,Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select v from Variant v where v.id=:id")
  Optional<Variant> lockById(@Param("id") Long id);   // row lock -> no overselling
}
