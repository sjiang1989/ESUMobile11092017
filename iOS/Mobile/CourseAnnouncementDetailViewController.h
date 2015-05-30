//
//  CourseAnnouncementDetailViewController.h
//  Mobile
//
//  Created by Jason Hocker on 6/6/13.
//  Copyright (c) 2013 Ellucian. All rights reserved.
//

#import <UIKit/UIKit.h>
#import "UIColor+SchoolCustomization.h"
#import "Module.h"
#import "CourseAnnouncement.h"
#import "DetailSelectionDelegate.h"

@interface CourseAnnouncementDetailViewController : UIViewController <DetailSelectionDelegate>//, UISplitViewControllerDelegate>

@property (nonatomic, strong) CourseAnnouncement *courseAnnouncement;

@property (weak, nonatomic) IBOutlet UILabel *dateLabel;
@property (weak, nonatomic) IBOutlet UILabel *courseNameLabel;
@property (weak, nonatomic) IBOutlet UITextView *descriptionTextView;
@property (weak, nonatomic) IBOutlet UILabel *titleLabel;
@property (weak, nonatomic) IBOutlet UIView *backgroundView;
@property (weak, nonatomic) IBOutlet UIToolbar *padToolBar;

@property (nonatomic, strong) UIPopoverController *popover;
@property (nonatomic, strong) UIPopoverController *masterPopover;

@property (strong, nonatomic) NSString *courseName;
@property (strong, nonatomic) NSString *courseSectionNumber;
@property (strong, nonatomic) NSString *itemTitle;
@property (strong, nonatomic) NSString *itemContent;
@property (strong, nonatomic) NSString *itemLink;
@property (strong, nonatomic) NSDate   *itemPostDateTime;


@property (strong, nonatomic) Module *module;

@end
