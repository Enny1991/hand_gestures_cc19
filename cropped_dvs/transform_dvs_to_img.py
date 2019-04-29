# -*- coding: utf-8 -*-
"""
Created on Sat Apr 27 15:45:07 2019

@author: E2MG
"""

from os import listdir
from os.path import isfile, join
import sys
import copy
import re
import numpy as np

from scipy.signal import stft
import scipy.io as sio
import numpy as np

import matplotlib.pyplot as plt

import pandas as pd
import csv

from sklearn.svm import SVC
from sklearn.decomposition import PCA
from sklearn.manifold import TSNE
from skimage import color
from skimage import io

from tqdm import tqdm
from utils import Person, simple_low_pass, exp_feat, window_spikes, analyze, do_tc_full
import pickle as pkl
import peakutils
import pywt

from jAER_utils.converter import aedat2numpy
from utils import analyze

import pandas as pd
import seaborn as sns

import cv2
import imutils
import csv

import random
import pickle



GENERATE_DATASET = True
GENERATE_TIFF = True


#-------------------------------------------------------
#-------------------------------------------------------


# general stuff
fs = 200  # sampling frequency of MYO
data_dir = './Dataset/'
CROP_SIZE = 40
classes = ['pinky', 'elle', 'yo', 'index', 'thumb']
classes_dict = dict([('pinky',0), ('elle',1), ('yo',2), ('index',3), ('thumb',4)])


#-------------------------------------------------------
#-------------------------------------------------------


# this is to find the zero timestepping of the davis
def find_trigger(ts):
    return np.where(np.diff(ts) < 0)[0][0]

def create_frame(x, y, dim=(128, 128)):
    img = np.zeros(dim)
    for _x, _y in zip(x.astype('int32'), y.astype('int32')):
        img[127 - _x,_y] += 1
    return np.log10(img / np.max(img) + 1e-10)


#-------------------------------------------------------
#    Generating the full dataset, takes some time...
#-------------------------------------------------------


names = [name for name in listdir(data_dir) if "emg" in name]
if GENERATE_DATASET:
    
    subjects = {}
    for name in names:
    
        _emg = np.load(data_dir + '{}'.format(name)).astype('float32')
        _ann = np.concatenate([np.array(['none']), np.load(data_dir + '{}'.format(name.replace("emg","ann")))[:-1]])
        
        subjects[name.split("_")[1]] = Person(name.split("_")[1], _emg, None, _ann, classes=classes)#subjects[names[0].split("_")[0][0:-1]] = Person(names[0].split("_")[0][0:-1], _emg, _dvs, _ann, classes=classes)

    
    print("Loaded EMG data")
    
    for name, data in subjects.items():
        for _class in classes:
            _annotation = np.float32(data.ann == _class)
            derivative = np.diff(_annotation)/1.0
            begins = np.where(derivative == 1)[0]
            ends = np.where(derivative == -1)[0]
            for b, e in zip(begins, ends):
                _trials = data.emg[b:e]
                data.trials[_class].append(_trials)
                data.begs[_class].append(b/fs)
                data.ends[_class].append(e/fs)
                
                #Acquiring DVS image
                events = aedat2numpy(data_dir + name +'_dvs.aedat')
                events = events[:, find_trigger(events[2]):]
                events[2] = events[2] / 1e3
                frame_size = 1.00
                shift = 1.
                beginning = b/fs + shift
                ending = beginning + frame_size
                sl = (events[2] > beginning) & (events[2] < ending)
                _dvs = events[:, sl]
                subjects[name].dvs[_class].append(_dvs)
                
                    
    print("Loaded DVS data")
    
    with open( './dump/dataset.pkl', 'wb') as f:
         pickle.dump(subjects, f)


#-------------------------------------------------------
#-------------------------------------------------------

    
with open( './dump/dataset.pkl', 'rb') as f:
     subjects = pickle.load(f)
     
if GENERATE_TIFF:
    for name in names:
        subject = name.split("_")[1]
        for gesture in classes:
            for trial in range(5):
                img = subjects[subject].dvs[gesture][trial]
                plt.imsave('./dump/img_src/'+subject+'_'+gesture+'_'+str(trial)+'.tiff', create_frame(img[1], img[0]))








