//
//  EventsFilterViewController.m
//  Mobile
//
//  Created by Jason Hocker on 8/7/12.
//  Copyright (c) 2012 Ellucian. All rights reserved.
//

#import "EventsFilterViewController.h"
#import "Event.h"
#import "EventCategory.h"
#import "UIViewController+GoogleAnalyticsTrackerSupport.h"

@interface EventsFilterViewController ()

@property (nonatomic,strong) NSSet *startingHiddenCategories;
@end

@implementation EventsFilterViewController
//@synthesize tableView = _tableView;

-(void)viewDidLoad
{
    [super viewDidLoad];
    
    self.startingHiddenCategories = [self.hiddenCategories copy];
    self.navigationController.navigationBar.translucent = NO;
    
}

- (void)viewWillAppear:(BOOL)animated
{
    [super viewWillAppear:animated];

    [self initializeCategories];
}

- (void)initializeCategories
{
    NSError *error;
    NSFetchRequest *categoryRequest = [[NSFetchRequest alloc] init];
    [categoryRequest setEntity:[NSEntityDescription entityForName:@"EventCategory" inManagedObjectContext:self.module.managedObjectContext ]];
    categoryRequest.predicate = [NSPredicate predicateWithFormat:@"moduleName = %@", self.module.name];
    NSMutableArray *categories = [[NSMutableArray alloc] init];
    NSArray *results = [self.module.managedObjectContext executeFetchRequest:categoryRequest error:&error ];
    for(EventCategory *category in results) {
        [categories addObject:category.name];
    }
    self.categories = [[categories copy] sortedArrayUsingSelector:@selector(localizedCaseInsensitiveCompare:)];
}

#pragma mark - Table view data source

- (NSInteger)numberOfSectionsInTableView:(UITableView *)tableView
{
    return 1;
}

- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section
{
    return [self.categories count];
}

- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath
{
    static NSString *CellIdentifier = @"Event Filter Cell";
    UITableViewCell *cell = [tableView dequeueReusableCellWithIdentifier:CellIdentifier];
    
    NSString *category = [self.categories objectAtIndex:[indexPath row]];
    UILabel *textLabel = (UILabel *)[cell viewWithTag:1];
    textLabel.text = category;
    
    if([self.hiddenCategories containsObject:category]) {
        cell.accessoryType = UITableViewCellAccessoryNone;
    } else {
        cell.accessoryType = UITableViewCellAccessoryCheckmark;
    }
    return cell;
}

#pragma mark - Table view delegate

- (void)tableView:(UITableView *)tableView didSelectRowAtIndexPath:(NSIndexPath *)indexPath
{
    UITableViewCell *cell = [tableView cellForRowAtIndexPath:indexPath];
    UILabel *textLabel = (UILabel *)[cell viewWithTag:1];
    if(cell.accessoryType == UITableViewCellAccessoryCheckmark) {
        cell.accessoryType = UITableViewCellAccessoryNone;
        [self.hiddenCategories addObject:textLabel.text];
    } else {
        cell.accessoryType = UITableViewCellAccessoryCheckmark;
        [self.hiddenCategories removeObject:textLabel.text];
    }
    
    [tableView deselectRowAtIndexPath:indexPath animated:YES];
    
    if (_delegate) {
        [self updateCategories];
        [_delegate reloadData];
    }
}

- (IBAction)dismiss:(id)sender {
    [self updateCategories];
    [self dismissViewControllerAnimated:YES completion:nil];
}

- (void)updateCategories
{
    NSError *error;
    if([self.hiddenCategories count] > 0) {
        self.eventModule.hiddenCategories = [[self.hiddenCategories allObjects] componentsJoinedByString:@","];
    } else {
        self.eventModule.hiddenCategories = nil;
    }
    
    if(![self.startingHiddenCategories isEqualToSet:self.hiddenCategories]) {
        [self sendEventToTracker1WithCategory:kAnalyticsCategoryUI_Action withAction:kAnalyticsActionList_Select withLabel:@"Filter changed" withValue:nil forModuleNamed:self.module.name];
    }
    
    [self.eventModule.managedObjectContext save:&error];
}

- (void)popoverControllerDidDismissPopover:(UIPopoverController *)popoverController
{
    if (_delegate)
    {
        [_delegate resetPopover];
    }
}

- (void)sizeForPopover {
    //Make row selections persist.
    self.clearsSelectionOnViewWillAppear = NO;
    
    NSInteger rowsCount = [_categories count];
    NSInteger singleRowHeight = [self.tableView.delegate tableView:self.tableView
                                           heightForRowAtIndexPath:[NSIndexPath indexPathForRow:0 inSection:0]];
    NSInteger totalRowsHeight = rowsCount * singleRowHeight;
    
    //Calculate how wide the view should be by finding how
    //wide each string is expected to be
    CGFloat largestLabelWidth = 200;
    for (NSString *catName in _categories) {
        //Checks size of text using the default font for UITableViewCell's textLabel.
        CGSize labelSize = [catName sizeWithAttributes: @{NSFontAttributeName: [UIFont boldSystemFontOfSize:20]}];
        if (labelSize.width > largestLabelWidth) {
            largestLabelWidth = labelSize.width;
        }
    }
    
    //Add a little padding to the width
    CGFloat popoverWidth = largestLabelWidth + 100;
    
    //Set the property to tell the popover container how big this view will be.
    self.preferredContentSize = CGSizeMake(popoverWidth, totalRowsHeight);
}

@end
