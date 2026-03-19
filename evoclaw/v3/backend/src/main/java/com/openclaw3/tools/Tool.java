package com.openclaw3.tools;

import java.util.Map;

public interface Tool {

    String getName();

    String getDescription();

    String getParameterSchema();

    Map<String, Object> call(Map<String, Object> args);
}
