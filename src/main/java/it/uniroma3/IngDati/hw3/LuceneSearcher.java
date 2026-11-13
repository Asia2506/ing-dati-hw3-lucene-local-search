package it.uniroma3.IngDati.hw3;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.LowerCaseFilterFactory;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.it.ItalianAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.pattern.PatternReplaceCharFilterFactory;
import org.apache.lucene.analysis.pattern.PatternTokenizerFactory;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

public class LuceneSearcher {
    
    // 1. Apertura della Directory e del Reader (Lettura dell'indice da disco)
    /* Scopo: aprire l'indice che abbiamo salvato su disco nella cartella index_dir. 
     * Poiché l'indice è un insieme di file binari immutabili (segmenti), usiamo un IndexReader 
     * (nello specifico, tramite un DirectoryReader che legge dalla nostra FSDirectory). */
    private static IndexReader getIndexReader(Path indexPath) throws IOException {
        Directory dir = FSDirectory.open(indexPath);
        return DirectoryReader.open(dir);
    }

    
    
    // 2. Configurazione dell'IndexSearcher e della Similarity (Ranking BM25)
    /* Scopo: eseguire le query effettive all'interno dei segmenti dell'indice, sfruttando l'IndexReader */
    private static IndexSearcher getIndexSearcher(IndexReader reader) {
        IndexSearcher searcher = new IndexSearcher(reader);
        
        // Impostiamo esplicitamente l'algoritmo di ranking BM25
        searcher.setSimilarity(new org.apache.lucene.search.similarities.BM25Similarity());
        
        return searcher;
    }
    
    

    // 3. Parsing della Query inserita dall'utente (Traduzione stringa -> Oggetto Query)
    /* Scopo: data una query utente, interpretarla secondo la sintassi di Lucene, passarla attraverso 
     * l'Analyzer corretto per tokenizzarla e trasformarla in un oggetto Query eseguibile dall'IndexSearcher. */
    public static Query parseQuery(String queryString, Analyzer analyzer) throws ParseException {
        // Creiamo il parser associandolo al campo di default "content" e al nostro wrapper di analyzer
        QueryParser parser = new QueryParser("content", analyzer);
        
        parser.setAllowLeadingWildcard(true);
        
        // Traduciamo la stringa testuale della console in un oggetto Query di Lucene
        return parser.parse(queryString);
    }

    
    
    // 4. Configurazione dell'Analyzer (Metodo estratto per essere riutilizzato)
    /* Scopo: creare e restituire l'Analyzer configurato. 
     * Essendo "public static", questo metodo può essere richiamato anche dalla classe AutomatedEvaluator, 
     * evitando di duplicare il codice di configurazione (Principio DRY). */
    public static Analyzer getAnalyzer() {
        Map<String, Analyzer> analyzerPerField = new HashMap<>();
        
        try {
            // Configurazione del CustomAnalyzer speculare a quello dell'Indexer.
            // Garantisce che la query inserita dall'utente sul campo "filename" venga tokenizzata 
            // con le stesse identiche regole (rimozione .txt e split sul trattino '-') usate in scrittura.
            Analyzer filenameAnalyzer = CustomAnalyzer.builder()
                .addCharFilter(PatternReplaceCharFilterFactory.class, "pattern", "(?i)\\.txt$", "replacement", "")
                .withTokenizer(PatternTokenizerFactory.class, "pattern", "\\s*-\\s*")
                .addTokenFilter(LowerCaseFilterFactory.class)
                .build();
                
            analyzerPerField.put("filename", filenameAnalyzer);
        } catch (Exception e) {
            // Gestione di sicurezza per eventuali errori di costruzione dell'analyzer custom
            System.err.println("Errore nella configurazione dell'analyzer per il filename: " + e.getMessage());
            analyzerPerField.put("filename", new StandardAnalyzer()); // Fallback
        }
        
        // Campo "content": usiamo l'ItalianAnalyzer (coerente con l'indexer)
        analyzerPerField.put("content", new ItalianAnalyzer());
        
        return new PerFieldAnalyzerWrapper(new StandardAnalyzer(), analyzerPerField);
    }
    
    

    // 5. Metodo per contare i risultati di una query (Per il test automatico)
    /* Scopo: eseguire una query e restituire solo il numero totale di risultati trovati (TotalHits).
     * Questo è utile per l'AutomatedEvaluator per compilare la tabella delle metriche.
     * Aggiunto throws IOException per gestire l'eccezione lanciata da searcher.search. */
    public static long eseguiRicercaEContaRisultati(String queryString, IndexSearcher searcher, Analyzer analyzer) throws ParseException, IOException {
        // 1. Facciamo il parsing della stringa
        Query query = parseQuery(queryString, analyzer);
        
        // 2. Eseguiamo la ricerca richiedendo solo 1 risultato (ci basta per leggere il totalHits.value)
        TopDocs topDocs = searcher.search(query, 1); 
        return topDocs.totalHits.value;
    }
    
    

    // 6. Metodo per eseguire e stampare i risultati (Richiamato dal main)
    /* Scopo: incapsulare la logica di ricerca e stampa a schermo. 
     * Questo rende il main più pulito e permette di riutilizzare questa logica se necessario. */
    public static void eseguiEStampaQuery(String queryString, IndexSearcher searcher, Analyzer analyzer) throws ParseException, IOException {
        // 1. Facciamo il parsing della stringa trasformandola in un oggetto Query
        Query query = parseQuery(queryString, analyzer);

        // 2. Eseguiamo la ricerca richiedendo i Top 10 risultati più rilevanti
        TopDocs topDocs = searcher.search(query, 10);

        System.out.println("Trovati " + topDocs.totalHits.value + " risultati in totale.");

        // 3. Iteriamo sui documenti trovati (ScoreDoc contiene l'ID interno e lo score BM25)
        int rank = 1;
        for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
            // Recuperiamo il Document originale sfruttando i campi salvati con Field.Store.YES
            Document doc = searcher.storedFields().document(scoreDoc.doc);
            
            String fileName = doc.get("filename");
            float score = scoreDoc.score;

            System.out.println(String.format(" [%d] File: %s (Score: %.4f)", rank++, fileName, score));
        }
        System.out.println(); // Spazio vuoto per leggibilità tra una query e l'altra
    }

    // 7. Esecuzione della ricerca e stampa dei risultati (IL MAIN)
    public static void main(String[] args) {
        // Percorso in cui si trova l'indice invertito creato dal LuceneIndexer
        Path indexPath = Paths.get("index_dir_Nuovo");

        // Controlliamo se la cartella dell'indice esiste prima di partire
        if (!Files.exists(indexPath)) {
            System.err.println("Errore: La cartella dell'indice '" + indexPath.toAbsolutePath() + "' non esiste!");
            System.err.println("Esegui prima la classe LuceneIndexer per generare l'indice.");
            return;
        }

        try {
            // Apriamo l'IndexReader e l'IndexSearcher
            IndexReader reader = getIndexReader(indexPath);
            IndexSearcher searcher = getIndexSearcher(reader);

            // Richiamiamo il metodo estratto per ottenere l'Analyzer
            Analyzer analyzer = getAnalyzer();

            // Usiamo un BufferedReader per leggere l'input dell'utente direttamente dalla console
            BufferedReader consoleInput = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            
            System.out.println("=== MOTORE DI RICERCA LUCENE PRONTO ===");
            System.out.println("Puoi inserire query come:");
            System.out.println(" - gatto (cerca nel campo di default 'content' con stemming italiano)");
            System.out.println(" - filename:cenerentola (cerca sul campo filename usando il CustomAnalyzer)");
            System.out.println(" - filename:*mercante* (cerca con wildcard sul titolo)");
            System.out.println("Digita 'exit' per uscire.\n");

            while (true) {
                System.out.print("Query > ");
                String queryString = consoleInput.readLine();

                // Condizione di uscita dalla console
                if (queryString == null || queryString.trim().equalsIgnoreCase("exit")) {
                    System.out.println("Chiusura del motore di ricerca. Arrivederci!");
                    break;
                }

                if (queryString.trim().isEmpty()) {
                    continue;
                }

                try {
                    // Richiamiamo il metodo per eseguire e stampare i risultati
                    eseguiEStampaQuery(queryString, searcher, analyzer);

                } catch (ParseException e) {
                    System.err.println("Errore di sintassi nella query: " + e.getMessage() + "\n");
                }
            }

            // Chiusura finale del reader
            reader.close();

        } catch (IOException e) {
            System.err.println("Errore di I/O durante la lettura dell'indice: " + e.getMessage());
            e.printStackTrace();
        }
    }
}