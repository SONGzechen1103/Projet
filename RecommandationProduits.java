package groupe4test;

import java.sql.*;
import java.util.*;

public class RecommandationProduits {

    // Informations de connexion à la base de données
    private static final String URL = "jdbc:mysql://localhost:3306/groupe4";
    private static final String USER = "root";
    private static final String PASSWORD = "123123";

    private Connection connection;

    // Définir les relations basées sur les associations des produits
    private static Map<String, List<String>> productAssociations = new HashMap<>();

    static {
        // Initialisation des associations de produits
        productAssociations.put("Pomme Golden", Arrays.asList("Pomme Granny smith", "Banane", "Tomate", "Citron"));
        productAssociations.put("Yaourt nature", Arrays.asList("Pack de yaourts aux fruits", "Pomme Golden", "Banane"));
        productAssociations.put("Bouteille d'eau 50cl",
                Arrays.asList("Chips nature", "Canette de Coca-Cola", "Canette de Pepsi"));
        productAssociations.put("Chips nature",
                Arrays.asList("Canette de Coca-Cola", "Bouteille d'eau 50cl", "Ketchup"));
        productAssociations.put("Tablette de chocolat", Arrays.asList("Barre chocolatee", "Bonbons", "Boite de the"));
        productAssociations.put("Beurre doux", Arrays.asList("Pain", "Emmental rape", "Moutarde de Dijon"));
        productAssociations.put("Pates Spaghetti", Arrays.asList("Riz basmati", "Pates Penne", "Poivre noir moulu"));
        productAssociations.put("Canette de Coca-Cola", Arrays.asList("Chips nature", "Barre chocolatee", "Bonbons"));
    }

    // Constructeur : initialiser la connexion à la base de données
    public RecommandationProduits() throws SQLException {
        this.connection = DriverManager.getConnection(URL, USER, PASSWORD);
    }

    // Fermer la connexion à la base de données
    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors de la fermeture de la connexion : " + e.getMessage());
        }
    }

    /**
     * Générer une recommandation combinée basée sur la similarité et les
     * associations.
     */
    public void generateCombinedRecommendations(int clientId) throws SQLException {
        // Étape 1 : Obtenir tous les produits (achetés et non achetés)
        List<Map<String, Object>> produits = getAllProduits(clientId);

        // Étape 2 : Séparer les produits achetés et les produits non achetés
        List<Map<String, Object>> produitsAchetes = new ArrayList<>();
        List<Map<String, Object>> produitsNonAchetes = new ArrayList<>();

        for (Map<String, Object> produit : produits) {
            if (produit.get("Status").equals("Achete")) {
                produitsAchetes.add(produit);
            } else {
                produitsNonAchetes.add(produit);
            }
        }

        if (produitsAchetes.isEmpty()) {
            System.out.println("Aucun produit trouvé pour ce client.");
            return;
        }

        // Étape 3 : Basé sur la similarité générer des recommandations (limité à 5
        // résultats)
        List<ProduitRecommande> similarityRecommendations = generateRecommendationsUsingSimilarity(produitsAchetes,
                produitsNonAchetes);

        // Étape 4 : Basé sur les associations générer des recommandations
        List<ProduitRecommande> associationRecommendations = generateRecommendationsUsingAssociations(produitsAchetes);

        // Étape 5 : Combiner les recommandations (supprimer les doublons)
        Set<String> existingProducts = new HashSet<>(); // Pour enregistrer les produits déjà recommandés
        List<ProduitRecommande> combinedRecommendations = new ArrayList<>();

        // Ajouter les recommandations basées sur la similarité
        for (ProduitRecommande produit : similarityRecommendations) {
            if (!existingProducts.contains(produit.getLibelle())) {
                combinedRecommendations.add(produit);
                existingProducts.add(produit.getLibelle());
            }
        }

        // Ajouter les recommandations basées sur les associations
        for (ProduitRecommande produit : associationRecommendations) {
            if (!existingProducts.contains(produit.getLibelle())) {
                combinedRecommendations.add(produit);
                existingProducts.add(produit.getLibelle());
            }
        }

        // Étape 6 : Afficher les résultats finaux des recommandations
        System.out.println("\nProduits recommandés pour vous (basés sur les deux algorithmes) :");
        for (ProduitRecommande produit : combinedRecommendations) {
            System.out.println(produit); // Utiliser le `toString()` défini dans ProduitRecommande
        }
    }

    /**
     * Obtenir tous les produits et marquer comme "Achete" ou "NonAchete"
     */
    private List<Map<String, Object>> getAllProduits(int clientId) throws SQLException {
        String query = """
                    SELECT p.LibelleP, p.Nutriscore, c.NomCAT AS Categorie, p.Marque, p.PrixunitaireP,
                           CASE WHEN co.ID_client IS NULL THEN 'NonAchete' ELSE 'Achete' END AS Status
                    FROM produit p
                    JOIN categorie c ON p.ID_CAT = c.ID_CAT
                    LEFT JOIN commander com ON p.ID_produit = com.Id_produit
                    LEFT JOIN commande co ON com.ID_CO = co.ID_CO AND co.ID_client = ?
                """;

        List<Map<String, Object>> produits = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, clientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Map<String, Object> produit = new HashMap<>();
                    produit.put("LibelleP", resultSet.getString("LibelleP"));
                    produit.put("Nutriscore", resultSet.getString("Nutriscore"));
                    produit.put("Categorie", resultSet.getString("Categorie"));
                    produit.put("Marque", resultSet.getString("Marque"));
                    produit.put("PrixunitaireP", resultSet.getDouble("PrixunitaireP"));
                    produit.put("Status", resultSet.getString("Status"));
                    produits.add(produit);
                }
            }
        }
        return produits;
    }

    /**
     * Générer des recommandations basées sur les associations
     */
    private List<ProduitRecommande> generateRecommendationsUsingAssociations(List<Map<String, Object>> produitsAchetes)
            throws SQLException {
        Set<ProduitRecommande> recommendations = new HashSet<>();
        for (Map<String, Object> produit : produitsAchetes) {
            String libelle = (String) produit.get("LibelleP");
            if (productAssociations.containsKey(libelle)) {
                for (String associatedProduct : productAssociations.get(libelle)) {
                    // Obtenir les détails du produit associé depuis la base de données
                    ProduitRecommande produitRecommande = getProductDetails(associatedProduct);
                    if (produitRecommande != null) {
                        recommendations.add(produitRecommande);
                    }
                }
            }
        }
        return new ArrayList<>(recommendations);
    }

    /**
     * Obtenir les détails d'un produit depuis la base de données
     */
    private ProduitRecommande getProductDetails(String libelle) throws SQLException {
        String query = """
                    SELECT p.LibelleP, p.Nutriscore, c.NomCAT AS Categorie, p.Marque, p.PrixunitaireP
                    FROM produit p
                    JOIN categorie c ON p.ID_CAT = c.ID_CAT
                    WHERE p.LibelleP = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, libelle);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new ProduitRecommande(
                            resultSet.getString("LibelleP"),
                            resultSet.getString("Nutriscore"),
                            resultSet.getString("Categorie"),
                            resultSet.getString("Marque"),
                            resultSet.getDouble("PrixunitaireP"),
                            0.0 // Les produits associés n'ont pas de score
                    );
                }
            }
        }
        return null;
    }

    /**
     * Générer des recommandations basées sur la similarité (limité à 5 résultats)
     */
    private List<ProduitRecommande> generateRecommendationsUsingSimilarity(
            List<Map<String, Object>> produitsAchetes,
            List<Map<String, Object>> produitsNonAchetes) {

        List<ProduitRecommande> recommandations = calculateSimilarities(produitsAchetes, produitsNonAchetes);

        // Trier par score décroissant et limiter à 5 résultats
        recommandations.sort((p1, p2) -> Double.compare(p2.getScore(), p1.getScore()));
        return recommandations.subList(0, Math.min(5, recommandations.size())); // Limiter à 5 résultats
    }

    /**
     * Calculer la similarité
     */
    private List<ProduitRecommande> calculateSimilarities(List<Map<String, Object>> produitsAchetes,
            List<Map<String, Object>> produitsNonAchetes) {
        List<ProduitRecommande> recommandations = new ArrayList<>();

        for (Map<String, Object> produitNonAchete : produitsNonAchetes) {
            double score = 0;

            for (Map<String, Object> produitAchete : produitsAchetes) {
                if (produitNonAchete.get("LibelleP").equals(produitAchete.get("LibelleP")))
                    score += 2;
                if (produitNonAchete.get("Nutriscore").equals(produitAchete.get("Nutriscore")))
                    score += 1.5;
                if (produitNonAchete.get("Categorie").equals(produitAchete.get("Categorie")))
                    score += 1.2;
                if (produitNonAchete.get("Marque").equals(produitAchete.get("Marque")))
                    score += 1;
                double prixDiff = Math.abs((double) produitNonAchete.get("PrixunitaireP")
                        - (double) produitAchete.get("PrixunitaireP"));
                if (prixDiff <= 1.0)
                    score += 0.8;
            }

            recommandations.add(new ProduitRecommande(
                    (String) produitNonAchete.get("LibelleP"),
                    (String) produitNonAchete.get("Nutriscore"),
                    (String) produitNonAchete.get("Categorie"),
                    (String) produitNonAchete.get("Marque"),
                    (double) produitNonAchete.get("PrixunitaireP"),
                    score));
        }
        return recommandations;
    }

    // Classe interne : produit recommandé
    static class ProduitRecommande {
        private final String libelle;
        private final String nutriscore;
        private final String categorie;
        private final String marque;
        private final double prix;
        private final double score;

        public ProduitRecommande(String libelle, String nutriscore, String categorie, String marque, double prix,
                double score) {
            this.libelle = libelle;
            this.nutriscore = nutriscore;
            this.categorie = categorie;
            this.marque = marque;
            this.prix = prix;
            this.score = score;
        }

        public String getLibelle() {
            return libelle;
        }

        public double getScore() {
            return score;
        }

        @Override
        public String toString() {
            return String.format("Produit : %s, Nutriscore : %s, Catégorie : %s, Marque : %s, Prix : %.2f",
                    libelle, nutriscore, categorie, marque, prix);
        }
    }

    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            RecommandationProduits recommander = new RecommandationProduits();

            System.out.print("Entrez l'ID du client pour obtenir des recommandations : ");
            int clientId = scanner.nextInt();

            // Générer les recommandations combinées
            recommander.generateCombinedRecommendations(clientId);
            recommander.closeConnection();

        } catch (SQLException e) {
            System.err.println("Erreur SQL : " + e.getMessage());
        }
    }
}
