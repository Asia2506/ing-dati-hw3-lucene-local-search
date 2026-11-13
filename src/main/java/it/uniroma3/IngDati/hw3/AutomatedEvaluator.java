package it.uniroma3.IngDati.hw3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

public class AutomatedEvaluator {

    public static void main(String[] args) {
        // Puntiamo alla cartella dell'indice generato dall'Indexer
        Path indexPath = Paths.get("index_dir_Nuovo");

        if (!Files.exists(indexPath)) {
            System.err.println("Errore: La cartella dell'indice '" + indexPath.toAbsolutePath() + "' non esiste!");
            System.err.println("Esegui prima la classe LuceneIndexer per generare l'indice.");
            return;
        }

        // Array con le 10 query di test richieste dalla consegna
        String[] queries = {
            "content:gatto",
            "content:\"c'era una volta\"",
            "filename:\"le mille e una notte\"",
            "filename:cenerentola",
            "filename:*mercante*",
            "filename:\"le mille e una notte\" AND content:genio",
            "content:amore -content:morte",
            "filename:\"i racconti delle fate\" OR filename:\"tutte le novelle\"",
            "content:correre",
            "filename:\"la cervia nel bosco\" AND (content:lupo OR content:fata)"
        };

        try {
            Directory dir = FSDirectory.open(indexPath);
            IndexReader reader = DirectoryReader.open(dir);
            IndexSearcher searcher = new IndexSearcher(reader);
            
            // Impostiamo l'algoritmo di ranking BM25
            searcher.setSimilarity(new org.apache.lucene.search.similarities.BM25Similarity());

            // Riuso dell'Analyzer centralizzato nel Searcher
            Analyzer analyzer = LuceneSearcher.getAnalyzer();

            System.out.println("=== VALUTAZIONE AUTOMATICA DELLE 10 QUERY DI TEST ===\n");
            System.out.println("| # | Query | Total Hits | Top Risultati (File & Score BM25) |");
            System.out.println("| :--- | :--- | :--- | :--- |");

            for (int i = 0; i < queries.length; i++) {
                String queryString = queries[i];
                try {
                    // 1. Sfruttiamo il metodo esistente nel Searcher per il conteggio totale
                    long totalHits = LuceneSearcher.eseguiRicercaEContaRisultati(queryString, searcher, analyzer);
                    
                    // 2. Per i dettagli dei top file, usiamo il PARSER del Searcher (non più un parser locale)
                    Query query = LuceneSearcher.parseQuery(queryString, analyzer);
                    TopDocs topDocs = searcher.search(query, 3);
                    
                    StringBuilder sb = new StringBuilder();
                    int rank = 1;
                    for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                        Document doc = searcher.storedFields().document(scoreDoc.doc);
                        String fileName = doc.get("filename");
                        sb.append(String.format("[%d] %s (%.2f) ", rank++, fileName, scoreDoc.score));
                    }
                    if (topDocs.scoreDocs.length == 0) {
                        sb.append("Nessun risultato");
                    }

                    // Stampiamo la riga formattata in Markdown pronta per la relazione
                    System.out.println(String.format("| %d | `%s` | %d | %s |", 
                            (i + 1), queryString, totalHits, sb.toString()));

                } catch (Exception e) {
                    System.out.println(String.format("| %d | `%s` | Errore | - |", (i + 1), queryString));
                }
            }

            System.out.println("\n=== FINE VALUTAZIONE AUTOMATICA ===");

            reader.close();
            dir.close();

        } catch (Exception e) {
            System.err.println("Errore critico durante la valutazione automatica: " + e.getMessage());
            e.printStackTrace();
        }
    }
}