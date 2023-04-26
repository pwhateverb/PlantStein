package com.plantstein.server.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.io.Serializable;
import java.util.List;


@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(RoomId.class)
public class Room {

    @Id
    private String name;

    @Id
    private String clientId;

    @OneToMany
    private List<Plant> plants;


}