package org.example;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;

public class FilerUtil {
  private static final String SERVICE_PATH = "META-INF/services";

  private FilerUtil() {}

  public static String getPath(String serviceName) {
    return SERVICE_PATH + "/" + serviceName;
  }

  public static Collection<String> readServiceFile(InputStream inputStream) throws IOException {
    try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {
      Collection<String> serviceClasses = new HashSet<>();
      String line;
      while ((line = bufferedReader.readLine()) != null) {
        int commentPos = line.indexOf('#');
        if (commentPos >= 0) {
          line = line.substring(0, commentPos);
        }
        line = line.trim();
        if (line.length() != 0) {
          serviceClasses.add(line);
        }
      }
      return serviceClasses;
    }
  }

  public static void writeServiceFile(Collection<String> services, OutputStream output)
      throws IOException {
    BufferedWriter writer =
        new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
    for (String service : services) {
      writer.write(service);
      writer.newLine();
    }
    writer.flush();
  }
}
