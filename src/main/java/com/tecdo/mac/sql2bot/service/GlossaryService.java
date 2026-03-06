package com.tecdo.mac.sql2bot.service;

import com.tecdo.mac.sql2bot.domain.Glossary;
import com.tecdo.mac.sql2bot.mapper.GlossaryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 术语库服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GlossaryService {

    private final GlossaryMapper glossaryMapper;

    public List<Glossary> listAll() {
        return glossaryMapper.selectAll();
    }

    public List<Glossary> listActive() {
        return glossaryMapper.selectActive();
    }

    public List<Glossary> listByCategory(String category) {
        return glossaryMapper.selectByCategory(category);
    }

    public Glossary getById(Long id) {
        return glossaryMapper.selectById(id);
    }

    public List<Glossary> search(String keyword) {
        return glossaryMapper.search(keyword);
    }

    @Transactional
    public Glossary create(Glossary glossary) {
        if (glossary.getIsActive() == null) {
            glossary.setIsActive(true);
        }
        glossaryMapper.insert(glossary);
        log.info("Created glossary: id={}, term={}", glossary.getId(), glossary.getTerm());
        return glossary;
    }

    @Transactional
    public void update(Glossary glossary) {
        glossaryMapper.update(glossary);
        log.info("Updated glossary: id={}", glossary.getId());
    }

    @Transactional
    public void delete(Long id) {
        glossaryMapper.deleteById(id);
        log.info("Deleted glossary: id={}", id);
    }
}
