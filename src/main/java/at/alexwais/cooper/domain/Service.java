package at.alexwais.cooper.domain;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
@EqualsAndHashCode(exclude = "containerTypes")
public class Service {

    private final String name;

    private List<ContainerType> containerTypes = new ArrayList<>();

}
