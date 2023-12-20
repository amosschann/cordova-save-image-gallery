//
//  SaveImageGallery.h
//  SaveImageGallery PhoneGap/Cordova plugin
//
//	Copyright (c) 2023 Tan Yi Jia <tanyijia@gmail.com>
//
//  Based on StefanoMagrassi's "Base64ToGallery.h"
//
//	MIT Licensed
//

#import <Cordova/CDV.h>

@interface SaveImageGallery : CDVPlugin

@property(nonatomic, copy) NSString* callbackId;
@property(nonatomic, assign) CDVPluginResult* result;

-(void) saveImageDataToLibrary : (CDVInvokedUrlCommand*) command;

@end
