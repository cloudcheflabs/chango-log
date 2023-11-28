package co.cloudcheflabs.chango.log;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class RunChangoLogTestRunner {

    @Test
    public void runChangoLog() throws Exception {

        String confPath = System.getProperty("confPath", "/Users/mykidong/project/chango-log/src/test/resources/configuration-test.yml");

        List<String> args = Arrays.asList(confPath);

        Chango.main(args.toArray(new String[0]));
    }
}
