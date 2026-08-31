package com.memorygraph.backend.memory.application;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.memorygraph.backend.memory.api.dto.PeopleGraphResponse;
import com.memorygraph.backend.memory.domain.PersonRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PeopleGraphService {

    private final PersonRepository people;
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public PeopleGraphResponse graph(UUID userId) {
        List<PeopleGraphResponse.Node> nodes = people.findAllForUser(userId).stream()
                .map(person -> new PeopleGraphResponse.Node(person.getId(), person.getDisplayName(),
                        people.countMemoriesForPerson(userId, person.getId())))
                .toList();

        List<PeopleGraphResponse.Edge> edges = jdbc.query("""
                select mp1.person_id as a, mp2.person_id as b, count(*) as shared
                from memory_people mp1
                join memory_people mp2
                  on mp1.memory_id = mp2.memory_id and mp1.person_id < mp2.person_id
                join memories m on m.id = mp1.memory_id
                where m.user_id = ?
                group by mp1.person_id, mp2.person_id
                """,
                (rs, rowNum) -> new PeopleGraphResponse.Edge(
                        (UUID) rs.getObject("a"),
                        (UUID) rs.getObject("b"),
                        rs.getLong("shared")),
                userId);

        return new PeopleGraphResponse(nodes, edges);
    }
}
