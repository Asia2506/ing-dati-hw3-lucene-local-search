package it.uniroma3.IngDati.hw3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.it.ItalianAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import java.nio.charset.StandardCharsets;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import java.util.stream.Stream;

public class LuceneIndexer {
	
	// 1. Scelta della Directory 
	/*Scopo: scegliere dove e come memorizziamo fisicamente i file binari (segmenti) che compongono l'indice invertito*/
    private static Directory getDirectory(Path indexPath) throws IOException{
        /*due opzioni:
         * FSDirectory (File System Directory): Salva l'indice fisicamente sul disco rigido all'interno della cartella specificata dal parametro indexPath. È persistente.
         * ByteBuffersDirectory (in memoria RAM): Salva l'indice interamente nella memoria volatile. L'accesso è istantaneo.*/
    	return FSDirectory.open(indexPath);
    }

    // 2. Scelta dell'Analyzer (Come processiamo linguisticamente i file)
    /*Scopo: prendere il testo grezzo e trasformarlo in "token" da inserire nell'indice invertito, 
     * applicando regole differenti per il filename e per il content.*/
    private static Analyzer getAnalyzer() {
        Map<String, Analyzer> analyzerPerField = new HashMap<>();
        
        try {
            // CUSTOM ANALYZER PER IL FILENAME: 
            // Progettato per gestire i titoli strutturati (es. "NomeLibro - TitoloCapitolo.txt")
            Analyzer filenameAnalyzer = CustomAnalyzer.builder()
                // 1. CharFilter: Rimuove l'estensione ".txt" (case-insensitive) alla fine della stringa prima della tokenizzazione
                .addCharFilter("patternReplace", "pattern", "(?i)\\.txt$", "replacement", "")
                
                // 2. Tokenizer: Utilizza un PatternTokenizer per dividere la stringa ESCLUSIVAMENTE 
                //    quando incontra il trattino '-', mantenendo intatti gli spazi all'interno delle parole.
                .withTokenizer("pattern", "pattern", "\\s*-\\s*")
                
                // 3. TokenFilter: Converte tutti i caratteri in minuscolo per normalizzare i token
                .addTokenFilter("lowercase")
                
                // 4. TokenFilter: Rimuove eventuali spazi bianchi superflui agli estremi dei token generati
                .addTokenFilter("trim")
                .build();
                
            analyzerPerField.put("filename", filenameAnalyzer);
            
        } catch (Exception e) {
            System.err.println("Errore nella creazione del CustomAnalyzer per il filename: " + e.getMessage());
            // Fallback di sicurezza in caso di errore nella costruzione del builder
            analyzerPerField.put("filename", new StandardAnalyzer());
        }
        
        // Campo "content": usiamo l'ItalianAnalyzer (rimuove stopword italiane e applica lo stemming)
        analyzerPerField.put("content", new ItalianAnalyzer());
        
        // Il PerFieldAnalyzerWrapper applica l'analizzatore specifico in base al campo del documento
        return new PerFieldAnalyzerWrapper(new StandardAnalyzer(), analyzerPerField);
    }

    // 3. Configurazione dell'IndexWriter (Policy di scrittura)
    /*Scopo: scrivere i dati all'interno della Directory (sul disco o in RAM). 
     * Prende i documenti, li passa attraverso l'Analyzer per tokenizzarli e genera i segmenti dell'indice invertito*/
    private static IndexWriter getIndexWriter(Directory dir, Analyzer analyzer) throws IOException {
    	// Configuriamo il writer associandogli l'analyzer scelto
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        
        // Impostiamo la policy: ricrea l'indice da zero a ogni esecuzione
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        
        // Restituiamo l'istanza dell'IndexWriter pronta all'uso
        return new IndexWriter(dir, config);
    }

    // 4. Creazione del Documento e scelta dei Field (Struttura dei dati)
    /*Scopo: prendere in input il percorso di un singolo file .txt dal dataset, 
     * leggerne il contenuto e trasformarlo in un oggetto Document di Lucene.*/
    private static Document createDocument(Path file) throws IOException{
    	Document doc = new Document();
        
        // 1. Campo filename: TextField analizzato dal nostro CustomAnalyzer personalizzato
        String fileName = file.getFileName().toString();
        doc.add(new TextField("filename", fileName, Field.Store.YES));
        
        // 2. Campo content: TextField viene analizzato dall'ItalianAnalyzer per la ricerca full-text
        String content = Files.readString(file, StandardCharsets.UTF_8);
        doc.add(new TextField("content", content, Field.Store.YES));
        
        return doc;
    }

    public static void main(String[] args) {
    	// Definiamo il percorso della cartella che contiene i file .txt delle storie
        Path docsPath = Paths.get("hw3_dataset_storie");
        
        // Definiamo il percorso della cartella in cui verrà salvato l'indice invertito pulito su disco
        Path indexPath = Paths.get("index_dir_Nuovo");

        // Controllo preliminare: verifichiamo che la cartella del dataset esista realmente
        if (!Files.exists(docsPath) || !Files.isDirectory(docsPath)) {
            System.err.println("Errore: La cartella del dataset '" + docsPath.toAbsolutePath() + "' non esiste!");
            return; // Interrompe l'esecuzione se manca la cartella dei dati
        }

        try {
            // Registriamo il tempo di inizio per calcolare le performance di indicizzazione
            long startTime = System.currentTimeMillis();

            // Chiamiamo i metodi di supporto che abbiamo scritto per preparare l'ambiente
            Directory dir = getDirectory(indexPath); // Apre la directory su disco
            Analyzer analyzer = getAnalyzer();       // Carica l'analyzer custom + ItalianAnalyzer
            IndexWriter writer = getIndexWriter(dir, analyzer); // Inizializza lo scrittore

            System.out.println("Indicizzazione dei file in corso...");

            // Apriamo uno stream per scorrere tutti i file presenti nella cartella del dataset
            try (Stream<Path> stream = Files.list(docsPath)) {
                // Filtriamo solo i file (escludendo eventuali sottocartelle) che terminano con ".txt"
                stream.filter(file -> !Files.isDirectory(file) && file.toString().endsWith(".txt"))
                      .forEach(file -> {
                          try {
                              // Per ogni file, creiamo il Documento Lucene con filename e content
                              Document doc = createDocument(file);
                              
                              // Aggiungiamo il documento all'IndexWriter (verrà indicizzato)
                              writer.addDocument(doc);
                              
                              System.out.println("Indicizzato: " + file.getFileName());
                          } catch (IOException e) {
                              System.err.println("Errore nell'indicizzazione del file " + file.getFileName() + ": " + e.getMessage());
                          }
                      });
            }

            // FONDAMENTALE: Chiudiamo il writer e la directory. 
            // Questo comando forza il flush dei dati e scrive fisicamente i segmenti dell'indice su disco.
            writer.close();
            dir.close();

            // Calcoliamo il tempo totale impiegato
            long endTime = System.currentTimeMillis();
            System.out.println("\nIndicizzazione completata con successo in " + (endTime - startTime) + " ms!");
            System.out.println("Indice salvato in: " + indexPath.toAbsolutePath());

            // Stampiamo un messaggio utile per la relazione
            System.out.println("\n[INFO RELAZIONE] Modello di analyzer applicato con successo. Estensione .txt rimossa e split basato unicamente su '-'.");

        } catch (IOException e) {
            System.err.println("Errore critico durante l'indicizzazione: " + e.getMessage());
            e.printStackTrace();
        }
    }
}