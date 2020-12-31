package at.alexwais.cooper.simulated;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class LoadRecord {
    private final int minutes;
    private final Map<String, Integer> externalServiceLoad = new HashMap<>();
}
