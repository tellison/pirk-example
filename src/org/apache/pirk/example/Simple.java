package org.apache.pirk.example;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.pirk.encryption.Paillier;
import org.apache.pirk.querier.wideskies.Querier;
import org.apache.pirk.querier.wideskies.decrypt.DecryptResponse;
import org.apache.pirk.querier.wideskies.encrypt.EncryptQuery;
import org.apache.pirk.query.wideskies.Query;
import org.apache.pirk.query.wideskies.QueryInfo;
import org.apache.pirk.responder.wideskies.standalone.Responder;
import org.apache.pirk.response.wideskies.Response;
import org.apache.pirk.schema.data.DataSchemaLoader;
import org.apache.pirk.schema.data.DataSchemaRegistry;
import org.apache.pirk.schema.query.QuerySchema;
import org.apache.pirk.schema.query.QuerySchemaLoader;
import org.apache.pirk.schema.query.QuerySchemaRegistry;
import org.apache.pirk.schema.response.QueryResponseJSON;
import org.apache.pirk.utils.SystemConfiguration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class Simple {

    // Characteristics of our query encryption.
    private static int hashBitSize = 12;
    private static String hashKey = "my key";
    private static int dataPartitionBitSize = 8;
    private static int paillierBitSize = 384;
    private static int certainty = 128;

    private static Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) throws Exception {

        /* Step 1: The querier builds an encrypted query. */

        // These are the search terms.
        ArrayList<String> selectors = new ArrayList<String>();
        selectors.add("Bob");
        selectors.add("Sam");

        // This is the data schema we will be querying over.
        try (InputStream is = new FileInputStream("dataschema.xml")) {
            DataSchemaRegistry.put(new DataSchemaLoader().loadSchema(is));
        }

        // This is the definition for the type of query. Look in the the
        // queryschema.xml file, and see we are requesting the name and age.
        String queryType = null;
        try (InputStream is = new FileInputStream("queryschema.xml")) {
            QuerySchema qs = new QuerySchemaLoader().loadSchema(is);
            queryType = qs.getSchemaName();
            QuerySchemaRegistry.put(qs);
        }

        // Create the base query info, and Paillier encryption definitions.
        QueryInfo queryInfo = new QueryInfo(selectors.size(), hashBitSize, hashKey, dataPartitionBitSize, queryType,
                false, false, false);

        Paillier paillier = new Paillier(paillierBitSize, certainty);

        // The /querier/ is kept secret by us in order to decrypt the results.
        Querier querier = new EncryptQuery(queryInfo, selectors, paillier).encrypt();
        debug(querier, "querier.json");

        // The encrypted /query/ is sent to the responder to perform the search.
        Query query = querier.getQuery();
        debug(query, "query.json");

        /* Step 2: Assume we have passed the query to the responder to run. */

        // The responder receives the encrypted query.
        Responder responder = new Responder(query);

        // Here we define the input data to load into memory, and the location
        // for storing results, though in general this would likely be a
        // reference to a big data set.
        SystemConfiguration.setProperty("pir.inputData", "datafile.json");
        SystemConfiguration.setProperty("pir.outputFile", "resultfile.ser");

        // Run the encrypted query to produce a response.
        responder.computeStandaloneResponse();

        // The response is still encrypted at this point.
        Response response = responder.getResponse();
        debug(response, "response.json");

        /* Step 3: The encrypted response is sent back to the querier. */

        // Decrypt the response using the information in our querier object.
        DecryptResponse decryptResponse = new DecryptResponse(response, querier);
        Map<String, List<QueryResponseJSON>> results = decryptResponse.decrypt();

        debug(results, "results.json");

        System.out.println("Finished");
    }

    /*
     * Helper to store objects in a file in JSON format.
     */
    private static void debug(Object obj, String fileName) throws IOException {
        (new File("debug")).mkdir();
        System.out.println("dumping " + obj);
        try (FileWriter io = new FileWriter("debug/" + fileName)) {
            io.write(gson.toJson(obj));
        }
    }
}
