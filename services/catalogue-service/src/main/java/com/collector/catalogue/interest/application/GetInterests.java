package com.collector.catalogue.interest.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.collector.catalogue.interest.application.port.InterestRepository;
import com.collector.catalogue.shared.application.port.MemberDirectory;
import com.collector.catalogue.shared.domain.Member;

@Service
public class GetInterests {

    private final MemberDirectory members;
    private final InterestRepository interests;

    public GetInterests(MemberDirectory members, InterestRepository interests) {
        this.members = members;
        this.interests = interests;
    }

    // Un membre qui n'a jamais rien écrit n'a pas de ligne app_user : liste vide,
    // et surtout aucune création sur une lecture (CA-2).
    @Transactional(readOnly = true)
    public List<InterestView> execute(Member member) {
        return members.findId(member.subject())
                .map(interests::detailedFor)
                .orElse(List.of());
    }
}
