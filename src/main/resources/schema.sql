CREATE TABLE IF NOT EXISTS smartphones (
    id IDENTITY PRIMARY KEY,
    marque VARCHAR(50),
    modele VARCHAR(50),
    os VARCHAR(30),
    annee_sortie INT,
    taille_ecran DOUBLE,
    prix DECIMAL(10, 2)
);