
package co.remi.asciiframe;

import io.vertx.core.json.JsonArray;
import java.util.ArrayList;
import java.util.List;

public class DocumentSpec {
    public final String entry;
    public final List<String> formats;

    public DocumentSpec(String entry, JsonArray formatsJa) {
        this.entry = entry;
        if (formatsJa == null) this.formats = List.of("html","pdf");
        else {
            List<String> f = new ArrayList<>();
            for (int i=0;i<formatsJa.size();i++) f.add(formatsJa.getString(i));
            this.formats = f;
        }
    }
}
