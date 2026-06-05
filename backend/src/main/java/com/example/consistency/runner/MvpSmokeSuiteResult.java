package com.example.consistency.runner;

import com.example.consistency.scenario.ScenarioType;

import java.util.List;

record MvpSmokeSuiteResult(List<MvpSmokeSuiteEntry> entries) {

    MvpSmokeSuiteResult {
        entries = List.copyOf(entries);
    }

    int scenarioCount() {
        return (int) entries.stream()
                .map(MvpSmokeSuiteEntry::scenario)
                .distinct()
                .count();
    }

    int entryCount() {
        return entries.size();
    }

    List<MvpSmokeSuiteEntry> entriesFor(ScenarioType scenario) {
        return entries.stream()
                .filter(entry -> entry.scenario() == scenario)
                .toList();
    }

    long brokenNaiveCount() {
        return entries.stream()
                .filter(MvpSmokeSuiteEntry::isNaiveFailure)
                .count();
    }

    long passingGuardedCount() {
        return entries.stream()
                .filter(MvpSmokeSuiteEntry::isGuardedPass)
                .count();
    }

    long asyncAcceptedCount() {
        return entries.stream()
                .filter(MvpSmokeSuiteEntry::isAsyncAccepted)
                .count();
    }

    long phase2EntryCount() {
        return entries.stream()
                .filter(entry -> entry.scenario() != ScenarioType.FIRST_LOGIN_REWARD
                        && entry.scenario() != ScenarioType.COUPON_CAMPAIGN_ISSUE
                        && entry.scenario() != ScenarioType.POINT_SPEND)
                .count();
    }
}

