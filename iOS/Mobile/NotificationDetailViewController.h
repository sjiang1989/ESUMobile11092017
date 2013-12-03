//
//  NotificationDetailViewController.h
//  Mobile
//
//  Created by Jason Hocker on 9/27/12.
//  Copyright (c) 2012 Ellucian. All rights reserved.
//

#import <UIKit/UIKit.h>
#import "Notification.h"
#import "WebViewController.h"
#import "UIColor+SchoolCustomization.h"
#import "Module.h"

@interface NotificationDetailViewController : UIViewController

@property (strong, nonatomic) Module *module;
@property (strong, nonatomic) Notification *notification;

@property (weak, nonatomic) IBOutlet UILabel *notificationTitleLabel;
@property (weak, nonatomic) IBOutlet UILabel *notificationDateLabel;

@property (weak, nonatomic) IBOutlet UITextView *notificationDetailTextView;
@property (weak, nonatomic) IBOutlet UIView *backgroundView;


@end
