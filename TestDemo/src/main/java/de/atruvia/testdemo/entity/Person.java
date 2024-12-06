package de.atruvia.testdemo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Person {
    private String lastName;
    private String firstName;
    private Integer age;
}
