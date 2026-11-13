import os
import re
import ebooklib
from ebooklib import epub
from bs4 import BeautifulSoup

# --- CONFIGURAZIONE ---
cartella_input = "." 
cartella_output = "hw3_dataset" 
os.makedirs(cartella_output, exist_ok=True)

PAROLE_IGNORATE = [
    "indice", "index", "colophon", "avvertenza", "introduzione", 
    "informazioni", "copertina", "conclusione", "note", "copyright", 
    "prefazione", "appendice", "bibliografia"
]

file_epub_list = [f for f in os.listdir(cartella_input) if f.endswith('.epub')]

if not file_epub_list:
    print("Nessun file .epub trovato.")
    exit()

def pulisci_base(testo_grezzo):
    # Rimuove caratteri speciali, mantiene lettere/numeri/spazi
    pulito = re.sub(r'[^a-zA-Z0-9À-ÿ\s]', ' ', testo_grezzo)
    pulito = re.sub(r'\s+', ' ', pulito).strip()
    return pulito.title()

def distruggi_sporcizia(testo, nome_libro_pulito=""):
    # 1. Rimuove "novelle arabe", "novelle a", "novelle" (case-insensitive)
    testo_pulito = re.sub(r'(?i)novelle\s*(arabe|a)?', '', testo)
    
    # 2. Rimuove il nome del libro se è stato erroneamente accodato al titolo del capitolo
    if nome_libro_pulito and nome_libro_pulito.lower() in testo_pulito.lower():
        testo_pulito = re.sub(r'(?i)' + re.escape(nome_libro_pulito), '', testo_pulito)
        
    # 3. Pulisce spazi doppi e applica Title Case
    testo_pulito = re.sub(r'\s+', ' ', testo_pulito).strip()
    return testo_pulito.title()

print(f"Trovati {len(file_epub_list)} file ePUB. Inizio l'estrazione...\n")

counter = 0

for epub_file in file_epub_list:
    print(f"Elaborazione del libro: {epub_file}...")
    filepath = os.path.join(cartella_input, epub_file)
    
    try:
        book = epub.read_epub(filepath)
        
        # --- 1. NOME DEL LIBRO FORZATO E PULITO ---
        nome_base = epub_file.replace(".epub", "").replace("_", " ")
        nome_libro_pulito = pulisci_base(nome_base)
        
        # Se contiene "mille e una notte", forziamo il nome perfetto
        if "mille e una notte" in nome_libro_pulito.lower():
            nome_libro_pulito = "Le Mille E Una Notte"
        elif "collodi" in nome_libro_pulito.lower():
            nome_libro_pulito = "I Racconti Delle Fate"
        elif "verga" in nome_libro_pulito.lower():
            nome_libro_pulito = "Tutte Le Novelle"

        for item in book.get_items():
            if item.get_type() == ebooklib.ITEM_DOCUMENT:
                soup = BeautifulSoup(item.get_content(), 'html.parser')
                testo = soup.get_text(separator='\n', strip=True)
                
                if len(testo) < 400:
                    continue

                # Estrae il titolo del capitolo
                titolo_tag = soup.find(['h1', 'h2', 'h3'])
                if titolo_tag:
                    capitolo_riferimento = titolo_tag.get_text(strip=True)
                else:
                    capitolo_riferimento = " ".join(testo.split()[:5])
                
                if any(parola in capitolo_riferimento.lower() for parola in PAROLE_IGNORATE):
                    continue
                
                # --- 2. PULIZIA DEL CAPITOLO ---
                titolo_pulito = pulisci_base(capitolo_riferimento)
                # Rimuove "novelle", "arabe", ecc. dal titolo
                titolo_pulito = distruggi_sporcizia(titolo_pulito, nome_libro_pulito)[:40].strip()
                
                if not titolo_pulito or titolo_pulito.lower() == nome_libro_pulito.lower():
                    titolo_pulito = f"Parte {counter+1}"

                # --- 3. CREAZIONE NOME FILE PERFETTO ---
                nome_file = f"{nome_libro_pulito} - {titolo_pulito}.txt"
                nome_file = re.sub(r'[\\/*?:"<>|]', "", nome_file)
                
                intestazione = f"Libro: {nome_libro_pulito}\nCapitolo: {titolo_pulito}\n{'-'*40}\n\n"
                contenuto_finale = intestazione + testo

                out_path = os.path.join(cartella_output, nome_file)
                
                if os.path.exists(out_path):
                    base, ext = os.path.splitext(nome_file)
                    nome_file = f"{base} {counter}{ext}"
                    out_path = os.path.join(cartella_output, nome_file)

                with open(out_path, 'w', encoding='utf-8') as f_out:
                    f_out.write(contenuto_finale)
                    
                counter += 1
                
    except Exception as e:
        print(f"  Errore durante l'elaborazione di {epub_file}: {e}")

print(f"\n✅ Fatto! Creati {counter} file .txt. I nomi sono stati ripuliti da 'novelle a/arabe'.")