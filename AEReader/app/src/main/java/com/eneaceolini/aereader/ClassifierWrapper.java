package com.eneaceolini.aereader;

import java.io.Serializable;

import smile.classification.AdaBoost;
import smile.classification.DecisionTree;
import smile.classification.KNN;
import smile.classification.LDA;
import smile.classification.LogisticRegression;
import smile.classification.NeuralNetwork;
import smile.classification.SVM;

public class ClassifierWrapper implements Serializable {
    LDA lda;
    SVM svm;
    LogisticRegression logit;
    DecisionTree tree;
    NeuralNetwork net;
    KNN knn;
    AdaBoost forest;

    public LDA getLDA(){
        return lda;
    }

    public SVM getSVM(){
        return svm;
    }

    public LogisticRegression getLogit(){
        return logit;
    }

    public DecisionTree getTree(){
        return tree;
    }

    public NeuralNetwork getNet(){
        return net;
    }

    public KNN getKNN(){
        return knn;
    }

    public AdaBoost getForest(){
        return forest;
    }

    public void setLDA(LDA lda){
        this.lda = lda;
    }

    public void setSVM(SVM svm){
        this.svm = svm;
    }

    public void setLogit(LogisticRegression logit){
        this.logit = logit;
    }

    public void setTree(DecisionTree tree){
        this.tree = tree;
    }

    public void setNet(NeuralNetwork net) {this.net = net; }

    public void netKNN(KNN knn) { this.knn = knn; }

    public void setAdaBoost(AdaBoost forest) { this.forest = forest; }
}


