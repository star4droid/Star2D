package com.star4droid.najma;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Test helper: Cli <file.njm> <relative path inside java/> [out.java] [elements: name=Type,...] ; prints errors, exit 1 when any. */
public final class Cli {
    public static void main(String[] a) throws Exception {
        String src = new String(Files.readAllBytes(Paths.get(a[0])), StandardCharsets.UTF_8);
        Najma.Options o = Najma.optionsFor(a[1]);
        if (a.length > 3 && !a[3].isEmpty()) {
            o.elements = new java.util.LinkedHashMap<>();
            for (String kv : a[3].split(",")) o.elements.put(kv.split("=")[0], kv.split("=")[1]);
        }
        long t0 = System.nanoTime();
        Najma.Result r = Najma.compile(src, o);
        long ms = (System.nanoTime() - t0) / 1000000;
        if (a.length > 2 && !a[2].isEmpty()) Files.write(Paths.get(a[2]), r.java.getBytes(StandardCharsets.UTF_8));
        else System.out.println(r.java);
        for (Najma.Diag d : r.errors) System.out.println("ERR " + d);
        System.err.println("time " + ms + " ms");
        System.exit(r.ok() ? 0 : 1);
    }
}
