package com.codecool.klondike;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.event.EventHandler;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.Pane;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public class Game extends Pane {

    private List<Card> deck = new ArrayList<>();

    private Pile stockPile;
    private Pile discardPile;
    private List<Pile> foundationPiles = FXCollections.observableArrayList();
    private List<Pile> tableauPiles = FXCollections.observableArrayList();

    private double dragStartX, dragStartY;
    private List<Card> draggedCards = FXCollections.observableArrayList();

    private static double STOCK_GAP = 1;
    private static double FOUNDATION_GAP = 0;
    private static double TABLEAU_GAP = 30;


    private EventHandler<MouseEvent> onMouseClickedHandler = e -> {
        Card card = (Card) e.getSource();
        if (card.getContainingPile().getPileType() == Pile.PileType.STOCK) {
            card.moveToPile(discardPile);
            card.flip();
            card.setMouseTransparent(false);
            System.out.println("Placed " + card + " to the waste.");
        }
    };

    private EventHandler<MouseEvent> stockReverseCardsHandler = e -> {
        refillStockFromDiscard();
    };

    private EventHandler<MouseEvent> onMousePressedHandler = e -> {
        dragStartX = e.getSceneX();
        dragStartY = e.getSceneY();
    };

    private EventHandler<MouseEvent> onMouseDraggedHandler = e -> {
        Card card = (Card) e.getSource();
        Pile activePile = card.getContainingPile();
        if (activePile.getPileType() == Pile.PileType.STOCK)
            return;
        double offsetX = e.getSceneX() - dragStartX;
        double offsetY = e.getSceneY() - dragStartY;

        draggedCards.clear();

        int cardsIndex = activePile.getCards().size();
        for (int i = 0; i < activePile.getCards().size(); i++) {
            if(activePile.getCards().get(i) == card){
                cardsIndex = i;
            }
            if(i>=cardsIndex){
                draggedCards.add(activePile.getCards().get(i));
            }
        }

        for (Card card1: draggedCards) {
            card1.getDropShadow().setRadius(20);
            card1.getDropShadow().setOffsetX(10);
            card1.getDropShadow().setOffsetY(10);

            card1.toFront();
            card1.setTranslateX(offsetX);
            card1.setTranslateY(offsetY);
        }

    };

    private EventHandler<MouseEvent> onMouseReleasedHandler = e -> {
        if ( draggedCards.isEmpty())
            return;
        Card card = (Card) e.getSource();
        Pile tableauPile = getValidIntersectingPile(card, tableauPiles);
        Pile foundationPile = getValidIntersectingPile(card, foundationPiles);

        if(isGameWon()){
            showPopupWIN();
        }

        if (tableauPile != null) {
            handleValidMove(card, tableauPile);
        } else if (foundationPile != null) {
            handleValidMove(card, foundationPile);
        } else {
            draggedCards.forEach(MouseUtil::slideBack);
            draggedCards.clear();
        }

    };

    public List<Pile> getTableauPiles() {
        return tableauPiles;
    }

    private void showPopupWIN() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Application information");
        alert.setHeaderText("Congratulations, you won!");
        alert.setContentText("Great job!");

        Optional<ButtonType> result = alert.showAndWait();

    }

    public boolean isGameWon() {
        int allCardsInAllFoundationPiles = 0;

        for (Pile pile : foundationPiles) {
            allCardsInAllFoundationPiles += pile.getCards().size();
        }

        System.out.println("CardsOn-F-Pile = " + allCardsInAllFoundationPiles );
        return (allCardsInAllFoundationPiles == 52);
    }

    public Game() {
        deck = Card.createNewDeck();
        initPiles();
        dealCards();
    }

    public void addMouseEventHandlers(Card card) {
        card.setOnMousePressed(onMousePressedHandler);
        card.setOnMouseDragged(onMouseDraggedHandler);
        card.setOnMouseReleased(onMouseReleasedHandler);
        card.setOnMouseClicked(onMouseClickedHandler);
    }

    public void refillStockFromDiscard() {
        int sizeOfDiscardPile = this.discardPile.numOfCards();
        for (int i = 0; i < sizeOfDiscardPile; i++) {
            this.discardPile.getTopCard().moveToPile(this.stockPile);
            this.stockPile.getTopCard().flip();
        }
        System.out.println("Stock refilled from discard pile.");
    }

    public boolean isMoveValid(Card card, Pile destPile) {
        if (destPile.getPileType() == Pile.PileType.FOUNDATION) {
            if(draggedCards.size()!=1){
                return false;
            }
            if (destPile.numOfCards() != 0) {
                return destPile.getTopCard().getSuit() == card.getSuit() && destPile.getTopCard().getRank().ordinal() + 1 == card.getRank().ordinal();
            } else {
                return card.getRank() == Card.Rank.ace;
            }
        } else if (destPile.getPileType() == Pile.PileType.TABLEAU) {
            if (destPile.numOfCards() != 0) {
                return destPile.getTopCard().getRank().ordinal() - 1 == card.getRank().ordinal() && Card.isOppositeColor(card, destPile.getTopCard());
            } else {
                return card.getRank() == Card.Rank.king;
            }
        }
        return false;
    }

    private Pile getValidIntersectingPile(Card card, List<Pile> piles) {
        Pile result = null;
        for (Pile pile : piles) {
            if (!pile.equals(card.getContainingPile()) &&
                    isOverPile(card, pile) &&
                    isMoveValid(card, pile))
                result = pile;
        }
        return result;
    }

    private boolean isOverPile(Card card, Pile pile) {
        if (pile.isEmpty())
            return card.getBoundsInParent().intersects(pile.getBoundsInParent());
        else
            return card.getBoundsInParent().intersects(pile.getTopCard().getBoundsInParent());
    }

    private void handleValidMove(Card card, Pile destPile) {
        String msg = null;
        if (destPile.isEmpty()) {
            if (destPile.getPileType().equals(Pile.PileType.FOUNDATION))
                msg = String.format("Placed %s to the foundation.", card);
            if (destPile.getPileType().equals(Pile.PileType.TABLEAU))
                msg = String.format("Placed %s to a new pile.", card);
        } else {
            msg = String.format("Placed %s to %s.", card, destPile.getTopCard());
        }
        System.out.println(msg);
        MouseUtil.slideToDest(draggedCards, destPile, this);
        draggedCards.clear();
    }


    private void initPiles() {
        stockPile = new Pile(Pile.PileType.STOCK, "Stock", STOCK_GAP);
        stockPile.setBlurredBackground();
        stockPile.setLayoutX(95);
        stockPile.setLayoutY(20);
        stockPile.setOnMouseClicked(stockReverseCardsHandler);
        getChildren().add(stockPile);

        discardPile = new Pile(Pile.PileType.DISCARD, "Discard", STOCK_GAP);
        discardPile.setBlurredBackground();
        discardPile.setLayoutX(285);
        discardPile.setLayoutY(20);
        getChildren().add(discardPile);

        for (int i = 0; i < 4; i++) {
            Pile foundationPile = new Pile(Pile.PileType.FOUNDATION, "Foundation " + i, FOUNDATION_GAP);
            foundationPile.setBlurredBackground();
            foundationPile.setLayoutX(610 + i * 180);
            foundationPile.setLayoutY(20);
            foundationPiles.add(foundationPile);
            getChildren().add(foundationPile);
        }
        for (int i = 0; i < 7; i++) {
            Pile tableauPile = new Pile(Pile.PileType.TABLEAU, "Tableau " + i, TABLEAU_GAP);
            tableauPile.setBlurredBackground();
            tableauPile.setLayoutX(95 + i * 180);
            tableauPile.setLayoutY(275);
            tableauPiles.add(tableauPile);
            getChildren().add(tableauPile);
        }
    }

    public void dealCards() {
        Iterator<Card> deckIterator = deck.iterator();
        Iterator<Pile> tableauIterator = tableauPiles.iterator();
        int size = 1;
        while (tableauIterator.hasNext()) {
            Pile tableau = tableauIterator.next();
            for (int i = 0; i < size; ++i ) {
                Card card = deckIterator.next();
                deckIterator.remove();
                tableau.addCard(card);
                addMouseEventHandlers(card);
                getChildren().add(card);
            }
            tableau.getTopCard().flip();
            ++size;
        }

        deckIterator.forEachRemaining(card -> {
            stockPile.addCard(card);
            addMouseEventHandlers(card);
            getChildren().add(card);
        });

    }

    public void setTableBackground(Image tableBackground) {
        setBackground(new Background(new BackgroundImage(tableBackground,
                BackgroundRepeat.REPEAT, BackgroundRepeat.REPEAT,
                BackgroundPosition.CENTER, BackgroundSize.DEFAULT)));
    }

}
