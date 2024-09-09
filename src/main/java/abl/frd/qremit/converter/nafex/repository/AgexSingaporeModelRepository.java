package abl.frd.qremit.converter.nafex.repository;

import abl.frd.qremit.converter.nafex.model.AgexSingaporeModel;
import abl.frd.qremit.converter.nafex.model.NafexEhMstModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AgexSingaporeModelRepository extends JpaRepository<AgexSingaporeModel, Integer> {
    Optional<AgexSingaporeModel> findByTransactionNoEqualsIgnoreCase(String transactionNo);
}