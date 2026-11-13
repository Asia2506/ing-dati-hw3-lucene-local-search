# 📚 HW3 - Motore di Ricerca Locale con Apache Lucene

Questo progetto implementa un sistema di Information Retrieval locale in Java utilizzando **Apache Lucene 9**[cite: 1]. Il sistema indicizza una directory di file `.txt`, creando due campi indicizzati distinti (`filename` e `content`) e permettendo di interrogarli tramite un'interfaccia a riga di comando (CLI) o di valutarli in modo automatizzato[cite: 1].

---

## ✨ Architettura del Sistema

Il progetto adotta una suddivisione modulare delle classi per rispettare il principio di separazione delle responsabilità e il principio DRY (Don't Repeat Yourself):

### 1. `LuceneIndexer` (Fase di Indicizzazione)
Si occupa della creazione e della scrittura dell'indice invertito:
* Utilizza una `FSDirectory` per salvare l'indice su disco nella cartella `index_dir_Nuovo`[cite: 1].
* Legge i file `.txt` e costruisce un oggetto `Document` per ciascuno, popolando i campi `filename` (memorizzato con `Field.Store.YES`) e `content`[cite: 1].
* Sfrutta un **`PerFieldAnalyzerWrapper`** per applicare analizzatori differenziati[cite: 1]:
  * **`ItalianAnalyzer`** sul campo `content` per gestire la lingua italiana, rimuovendo le stopword e applicando lo *stemming*[cite: 1, 7].
  * **`CustomAnalyzer`** sul campo `filename` (configurato con `PatternReplaceCharFilter` per rimuovere l'estensione `.txt` e un `PatternTokenizer` per suddividere il titolo dell'opera dal capitolo basandosi sul trattino `-`)[cite: 1, 7].

### 2. `LuceneSearcher` (Fase di Ricerca)
Fornisce il motore d'interrogazione testuale e interattiva:
* Apre l'indice tramite `DirectoryReader` e istanzia un `IndexSearcher` configurato esplicitamente con l'algoritmo di ranking **BM25** (`BM25Similarity`)[cite: 2].
* Traduce le stringhe in oggetti `Query` tramite il `QueryParser`, centralizzando la logica di parsing[cite: 2, 8].
* Supporta l'abilitazione delle wildcard iniziali (`parser.setAllowLeadingWildcard(true)`)[cite: 1, 8].
* Esegue la ricerca mostrando i Top-10 risultati con i relativi punteggi di pertinenza (Score BM25)[cite: 2, 8].

### 3. `AutomatedEvaluator` (Test Automatico)
Classe di supporto per il testing quantitativo:
* Esegue un set di 10 query preimpostate richiamando direttamente i metodi di ricerca del `LuceneSearcher` (senza duplicazione di codice)[cite: 2, 7].
* Estrae il numero totale di documenti trovati (`TotalHits`) e i top file correlati, stampando una tabella di valutazione pronta per la relazione[cite: 3, 7].

---

## 📂 Costruzione del Dataset (`hw3_dataset_storie`)

Il corpus di valutazione è composto da **168 documenti** in formato `.txt` in lingua italiana[cite: 1]:
* **Estrazione:** I file grezzi sono stati estratti da opere letterarie di pubblico dominio in formato ePub (*Le Mille e una Notte*, *I Racconti delle Fate*, *Tutte le Novelle*, *40 Novelle*) tramite uno script Python dedicato (`ebooklib` e `BeautifulSoup`)[cite: 1].
* **Data Cleaning:** Sono state scartate le sezioni di servizio (indici, introduzioni, colophon) e i nomi dei file sono stati normalizzati nel formato standard `Titolo Libro - Titolo Capitolo.txt` per consentire una corretta tokenizzazione basata sul trattino[cite: 1].

---

## 🔍 Sintassi delle Query Supportate

All'avvio del `LuceneSearcher`, è possibile digitare le seguenti tipologie di query:

* **Ricerche Full-Text (sul contenuto):**
  * `content:gatto` — Cerca la parola "gatto" (o varianti morfologiche grazie allo stemming)[cite: 1].
  * `content:"c'era una volta"` — *Phrase Query*, cerca la sequenza esatta di parole[cite: 1].
  * `content:correre` — Cerca il termine riconducendolo alla radice comune[cite: 1].

* **Ricerche sul Nome del File:**
  * `filename:cenerentola` — Cerca un capitolo specifico[cite: 1].
  * `filename:"le mille e una notte"` — Cerca tutti i capitoli di una specifica raccolta[cite: 1].
  * `filename:mercante*` — Cerca utilizzando i caratteri jolly (*wildcard*)[cite: 1].

* **Query Booleane e Complesse:**
  * `content:amore AND filename:"racconti"` — Intersezione logica tra titolo e contenuto[cite: 1].
  * `content:amore -content:morte` — Operatore di sottrazione/negazione (`NOT`)[cite: 1].
  * `filename:"i racconti delle fate" OR filename:"tutte le novelle"` — Unione logica di più raccolte[cite: 1].
  * `filename:"la cervia nel bosco" AND (content:lupo OR content:fata)` — Query complessa con raggruppamento booleano[cite: 1].

---

## 📊 Valutazione Sperimentale (Recall & Precision)

Il sistema è stato valutato confrontando i risultati restituiti da Lucene con la *Ground Truth* verificata manualmente sul file system[cite: 1]:

| # | Query Testata | Total Hits (Lucene) | Ground Truth | Recall | Precision |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **1** | `content:gatto` | 15 | 15 | 1.0 | 1.0 |[cite: 1]
| **2** | `content:"c'era una volta"` | 142 | 17 | 1.0 | 0.12 |[cite: 1]
| **3** | `filename:"le mille e una notte"` | 58 | 58 | 1.0 | 1.0 |[cite: 1]
| **4** | `filename:cenerentola` | 1 | 1 | 1.0 | 1.0 |[cite: 1]
| **5** | `filename:mercante*` | 2 | 2 | 1.0 | 1.0 |[cite: 1]
| **6** | `filename:"le mille e una notte" AND content:genio` | 14 | 14 | 1.0 | 1.0 |[cite: 1]
| **7** | `content:amore -content:morte` | 32 | 28 | 1.0 | 0.88 |[cite: 1]
| **8** | `filename:"i racconti delle fate" OR filename:"tutte le novelle"` | 106 | 106 | 1.0 | 1.0 |[cite: 1]
| **9** | `content:correre` | 54 | 51 | 0.94 | 0.94 |[cite: 1]
| **10** | `filename:"la cervia nel bosco" AND (content:lupo OR content:fata)` | 1 | 1 | 1.0 | 1.0 |[cite: 1]

### 💡 Analisi delle Anomalie e dei Risultati
* **Precisione Elevata (7 su 10 query identiche alla Ground Truth):** Dimostra l'efficacia del `CustomAnalyzer` strutturato sul nome file e la solidità degli operatori booleani[cite: 1].
* **Phrase Query e Stopword (Query 2):** L'`ItalianAnalyzer` rimuove le stopword anche nelle phrase query riducendo la stringa alla sola parola "volta", il che genera un numero elevato di falsi positivi (142 risultati)[cite: 1].
* **Stemming (Query 7 e 9):** L'espansione morfologica operata dall'analyzer riconduce i verbi e i sostantivi alla radice comune, aumentando la *Recall* a scapito di una leggera flessione della *Precision*[cite: 1].

---

## 🛠️ Prerequisiti e Avvio

* **Java 17** o superiore[cite: 1].
* **Maven** per la gestione delle dipendenze e la compilazione[cite: 1].

### Compilazione ed Esecuzione
1. **Indicizzazione:** Eseguire la classe `LuceneIndexer` per popolare la cartella dell'indice su disco.
2. **Ricerca Interattiva:** Eseguire la classe `LuceneSearcher` per avviare la CLI.
3. **Valutazione Automatica:** Eseguire la classe `AutomatedEvaluator` per stampare la tabella dei test quantitativi.
