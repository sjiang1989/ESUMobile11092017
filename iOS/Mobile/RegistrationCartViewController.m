//
//  RegistrationCartViewController.m
//  Mobile
//
//  Created by jkh on 11/18/13.
//  Copyright (c) 2013 - 2014 Ellucian. All rights reserved.
//

#import "RegistrationCartViewController.h"
#import "MBProgressHUD.h"
#import "RegistrationPlannedSection.h"
#import "Module+Attributes.h"
#import "CurrentUser.h"
#import "AuthenticatedRequest.h"
#import "RegistrationPlannedSectionInstructor.h"
#import "RegistrationPlannedSectionMeetingPattern.h"
#import "RegistrationTerm.h"
#import <AddressBook/AddressBook.h>
#import "MBProgressHUD.h"
#import "RegistrationResultsViewController.h"
#import "NSMutableURLRequest+BasicAuthentication.h"
#import "AppDelegate.h"
#import "RegistrationTabBarController.h"
#import "UIViewController+GoogleAnalyticsTrackerSupport.h"
#import "RegistrationPlannedSectionDetailViewController.h"

@interface RegistrationCartViewController ()
@property (strong, nonatomic) UIBarButtonItem *registerButton;
@property (nonatomic, strong) NSNumberFormatter *creditsFormatter;
@property (nonatomic, strong) NSString *termNeedingPIN;
@end

@implementation RegistrationCartViewController

-(void) viewDidAppear:(BOOL)animated
{
    [super viewDidAppear:animated];
    [self sendView:@"Registration Cart list" forModuleNamed:self.module.name];
    [self updateStatusBar];
}

- (void)viewDidLoad
{
    [super viewDidLoad];
    
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(reloadData:) name:kRegistrationPlanDataReloaded object:nil];

    self.navigationItem.title = [self.module name];

    UIBarButtonItem *flexibleItem = [[UIBarButtonItem alloc] initWithBarButtonSystemItem:UIBarButtonSystemItemFlexibleSpace target:self action:nil];
    self.registerButton = [[UIBarButtonItem alloc] initWithTitle:NSLocalizedString(@"Register", "Register button") style:UIBarButtonItemStyleBordered target:self action:@selector(startRegistration:)];

    UIImage *registerButtonImage = [UIImage imageNamed:@"Registration Button"];
    [self.navigationController.toolbar setBackgroundImage:registerButtonImage forToolbarPosition:UIToolbarPositionBottom barMetrics:UIBarMetricsDefault];
    
    self.toolbarItems = [NSArray arrayWithObjects:flexibleItem, self.registerButton, flexibleItem, nil];
    self.navigationController.navigationBar.translucent = NO;
    
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(refreshTable:) name:kRegistrationItemRemovedFromCart object:nil];
}

#pragma mark - variables from tab
-(RegistrationTabBarController *) registrationTabController
{
    return  (RegistrationTabBarController *)[self tabBarController];
}

-(NSString *) planId
{
    return self.registrationTabController.planId;
}

-(BOOL) registrationAllowed
{
    return self.registrationTabController.registrationAllowed;
}

#pragma mark - table

- (NSIndexPath *)tableView:(UITableView *)tableView willSelectRowAtIndexPath:(NSIndexPath *)indexPath {
    // rows in section 0 should not be selectable
    if ( indexPath.section == 0 ) return nil;
    return indexPath;
}


- (UITableViewCell *)tableView:(UITableView *)tableView configureCell:(NSIndexPath *)indexPath
{
    if(indexPath.section == 0) {
        UITableViewCell *cell = [tableView dequeueReusableCellWithIdentifier:@"Registration Ineligible Cell"];
        UILabel *label = (UILabel *)[cell viewWithTag:20];
        label.text = self.registrationTabController.ineligibleMessage;
        cell.selectionStyle = UITableViewCellSelectionStyleNone;
        [cell.contentView setNeedsLayout];
        [cell.contentView layoutIfNeeded];
        return cell;
    }
    RegistrationTerm *term = [self.registrationTabController.terms objectAtIndex:indexPath.section - 1];
    NSArray *plannedSections = [self.registrationTabController sectionsInCart:term.termId];
    RegistrationPlannedSection *plannedSection = [plannedSections objectAtIndex:indexPath.row];
    
    static NSString *CellIdentifier = @"Registration Planned Course Cell";
    
    UITableViewCell *cell = [tableView dequeueReusableCellWithIdentifier:CellIdentifier];
    if (cell == nil) {
        cell = [[UITableViewCell alloc] initWithStyle:UITableViewCellStyleDefault reuseIdentifier:CellIdentifier];
        cell.accessoryType = UITableViewCellAccessoryNone;
    }
    
    UILabel *line1aLabel = (UILabel *)[cell viewWithTag:1];
    line1aLabel.text = [NSString stringWithFormat:NSLocalizedStringWithDefaultValue(@"course name-section number", @"Localizable", [NSBundle mainBundle], @"%@-%@", @"course name-section number"), plannedSection.courseName, plannedSection.courseSectionNumber];
    UILabel *line1bLabel = (UILabel *)[cell viewWithTag:6];
    if(plannedSection.instructionalMethod) {
        line1bLabel.text = [NSString stringWithFormat:@"(%@)", plannedSection.instructionalMethod];
    } else
    {
        line1bLabel.text = nil;
    }
    UILabel *line2Label = (UILabel *)[cell viewWithTag:2];
    line2Label.text = plannedSection.sectionTitle;
    UILabel *line3Label = (UILabel *)[cell viewWithTag:3];
    UILabel *line3bLabel = (UILabel *)[cell viewWithTag:5];
    NSString *faculty = [plannedSection facultyNames];
    
    NSString *credits = [self.creditsFormatter stringFromNumber:plannedSection.credits];
    NSString *ceus = [self.creditsFormatter stringFromNumber:plannedSection.ceus];
    NSString *gradingType = @"";
    if (plannedSection.isAudit) {
        gradingType = [NSString stringWithFormat: @"%@", NSLocalizedString(@"| Audit", @"Audit label for registration")];
    }
    else if (plannedSection.isPassFail) {
        gradingType = [NSString stringWithFormat: @"%@", NSLocalizedString(@"| P/F", @"PassFail abbrev label for registration cart")];
    }

    
    if(faculty) {
        line3Label.text = [NSString stringWithFormat:@"%@", faculty];
        if (credits)
            line3bLabel.text = [NSString stringWithFormat:NSLocalizedString(@" | %@ Credits %@", @"| credits and Credits label and grading type for registration"), credits, gradingType ];
        else if (ceus) {
            line3bLabel.text = [NSString stringWithFormat:NSLocalizedString(@" | %@ CEUs %@", @"| ceus and CEUs label and grading type for registration"), ceus, gradingType ];
        }
    } else {
        if (credits) {
            line3Label.text = [NSString stringWithFormat:NSLocalizedString(@"%@ Credits %@", @"credits and Credits label and grading type for registration"), credits, gradingType];
        }
        else if (ceus) {
            line3Label.text = [NSString stringWithFormat:NSLocalizedString(@"%@ CEUs %@", @"ceus and CEUs label and grading type for registration"), ceus, gradingType];
        }
        line3bLabel.text = nil;
    }
    UILabel *line4Label = (UILabel *)[cell viewWithTag:4];
    if(plannedSection.meetingPatternDescription) {
        line4Label.text = [NSString stringWithFormat:@"%@", plannedSection.meetingPatternDescription];
    } else {
        line4Label.text = nil;
    }
    
    UIImageView *checkmarkImageView = (UIImageView *)[cell viewWithTag:100];
    if(plannedSection.selectedInCart) {
        checkmarkImageView.image = [UIImage imageNamed:@"Registration Checkmark"];
    } else {
        checkmarkImageView.image = [UIImage imageNamed:@"Registration Circle"];
    }
    
    //    if ([[[UIDevice currentDevice] systemVersion] floatValue] >= 7) {
    //        cell.accessoryType = UITableViewCellAccessoryDetailButton;
    //    } else {
    //        cell.accessoryType = UITableViewCellAccessoryDetailDisclosureButton;
    //    }
    //    cell.accessoryView = [[UIImageView alloc ] initWithImage: [UIImage imageNamed:@"Registration Detail"]];
    
    UIImage *image = [UIImage imageNamed:@"Registration Detail"];
    UIButton *button = [UIButton buttonWithType:UIButtonTypeCustom];
    CGRect frame = CGRectMake(0.0, 0.0, 44.0f, 44.0f);
    button.frame = frame;
    [button setImage:image forState:UIControlStateNormal];
    
    [button addTarget: self
               action: @selector(accessoryButtonTapped:withEvent:)
     forControlEvents: UIControlEventTouchUpInside];
    cell.accessoryView = button;
    
    cell.selectionStyle = UITableViewCellSelectionStyleNone;
    
    return cell;
}

- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath
{
    UITableViewCell *cell = [self tableView:tableView configureCell:indexPath];
    return cell;
}

- (void)tableView:(UITableView *)tableView didSelectRowAtIndexPath:(NSIndexPath *)indexPath
{
    RegistrationTerm *term = [self.registrationTabController.terms objectAtIndex:indexPath.section - 1];
    if(term.requiresAltPin && !term.userEnteredPIN) {
        [self promptForPIN:term.termId];
    }
    NSArray *plannedSections = [self.registrationTabController sectionsInCart:term.termId];
    RegistrationPlannedSection *plannedSection = [plannedSections objectAtIndex:indexPath.row];
    plannedSection.selectedInCart = !plannedSection.selectedInCart;
    [tableView reloadRowsAtIndexPaths:[NSArray arrayWithObject:indexPath] withRowAnimation:UITableViewRowAnimationNone];
    [self updateStatusBar];
}

- (NSString *)tableView:(UITableView *)tableView stringForTitleForHeaderInSection:(NSInteger)section
{
    if(section > 0) {
        RegistrationTerm *term = [self.registrationTabController.terms objectAtIndex:section - 1];
        return term.name;
    } else {
        return NSLocalizedString(@"Ineligible for Registration", @"Ineligible for Registration");
    }
}

- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section {
    if(section == 0) {
        return self.registrationTabController.ineligibleMessage ? 1 : 0;
    }
    RegistrationTerm *term = [self.registrationTabController.terms objectAtIndex:section - 1];
    NSArray *plannedSections = [self.registrationTabController sectionsInCart:term.termId];
    return [plannedSections count];
}

- (NSInteger)numberOfSectionsInTableView:(UITableView *)tableView
{
    return [self.registrationTabController.terms count] + 1;
}

- (void) accessoryButtonTapped: (UIControl *) button withEvent: (UIEvent *) event
{
    NSIndexPath * indexPath = [self.tableView indexPathForRowAtPoint: [[[event touchesForView: button] anyObject] locationInView: self.tableView]];
    if ( indexPath == nil )
        return;
    
    [self.tableView.delegate tableView: self.tableView accessoryButtonTappedForRowWithIndexPath: indexPath];
}

- (void)tableView:(UITableView *)tableView accessoryButtonTappedForRowWithIndexPath:(NSIndexPath *)indexPath
{
    RegistrationTerm *term = [self.registrationTabController.terms objectAtIndex:indexPath.section - 1];
    NSArray *plannedSections = [self.registrationTabController sectionsInCart:term.termId];
    RegistrationPlannedSection *plannedSection = [plannedSections objectAtIndex:indexPath.row];
    
    if (UI_USER_INTERFACE_IDIOM() == UIUserInterfaceIdiomPad) {
        UISplitViewController *split = [self.registrationTabController childViewControllers][0];
        split.presentsWithGesture = YES;
    
        UINavigationController *controller = split.viewControllers[0];
        UINavigationController *detailNavController = split.viewControllers[1];
    
        UIViewController *masterController = controller.topViewController;
        UIViewController *detailController = detailNavController.topViewController;
    
        if([masterController conformsToProtocol:@protocol(UISplitViewControllerDelegate)]) {
            split.delegate = (id)masterController;
        }
        if([detailController conformsToProtocol:@protocol(UISplitViewControllerDelegate)]) {
            split.delegate = (id)detailController;
        }
        if( [detailController conformsToProtocol:@protocol(DetailSelectionDelegate)]) {
            if ( [masterController respondsToSelector:@selector(detailSelectionDelegate) ])
            {
                [masterController setValue:detailController forKey:@"detailSelectionDelegate"];
            }
        }
        if (_detailSelectionDelegate) {
            [_detailSelectionDelegate selectedDetail:plannedSection withIndex:indexPath withModule:self.module withController:self];
        }
    }
    else if (UI_USER_INTERFACE_IDIOM() == UIUserInterfaceIdiomPhone) {
        [self performSegueWithIdentifier:@"Show Section Detail" sender:plannedSection];
    }
    
}

- (CGFloat)tableView:(UITableView *)tableView heightForHeaderInSection:(NSInteger)section {
    switch(section) {
        case 0:
            return 0.0f;
        default:
            return [super tableView:tableView heightForHeaderInSection:section];
    }
}

- (CGFloat)tableView:(UITableView *)tableView heightForRowAtIndexPath:(NSIndexPath *)indexPath
{
    switch (indexPath.section) {
        case 0:
        {
            UITableViewCell *cell = [self tableView:tableView configureCell:indexPath];
            CGFloat height = [cell.contentView systemLayoutSizeFittingSize:UILayoutFittingCompressedSize].height;
            return height;
            break;
        }
        default:
            return 86.0f;
    }
}


#pragma mark - logic
-(void) updateBadge
{
    int count = [self.registrationTabController itemsInCartCount];
    
    if(!self.registrationTabController.ineligibleMessage && count == 0){
        [self showNoDataView:NSLocalizedString(@"Registration Cart Empty", @"empty registration cart message")];
    } else {
        [self hideNoDataView];
    }

    NSString *badgeValue = [NSString stringWithFormat:@"%d", count];
    [[[[[self tabBarController] tabBar] items] objectAtIndex:0] setBadgeValue:badgeValue];
}

- (void)prepareForSegue:(UIStoryboardSegue *)segue sender:(id)sender
{
    [self.navigationController setToolbarHidden:YES animated:YES];
    if ([[segue identifier] isEqualToString:@"Show Section Detail"]) {
        RegistrationPlannedSection *courseSection = sender;
        RegistrationPlannedSectionDetailViewController *detailController = [segue destinationViewController];
        detailController.registrationPlannedSection = courseSection;
    } else if ([[segue identifier] isEqualToString:@"Register"]) {
        NSDictionary *messages = (NSDictionary *)sender;
        id detailController = [segue destinationViewController];
        if([detailController isKindOfClass:[UINavigationController class]]) {
            detailController = ((UINavigationController *)detailController).childViewControllers[0];
        }
        
        RegistrationResultsViewController *resultsViewController = (RegistrationResultsViewController *)detailController;
        resultsViewController.module = self.module;
        resultsViewController.importantMessages = [messages objectForKey:@"messages"];
        resultsViewController.registeredMessages = [messages objectForKey:@"successes"];
        resultsViewController.warningMessages = [messages objectForKey:@"failures"];
        resultsViewController.delegate = self.registrationTabController;
        
        for(NSDictionary *registeredMessage in [messages objectForKey:@"successes"]) {
            NSString *section = [registeredMessage objectForKey:@"sectionId"];
            NSString *term = [registeredMessage objectForKey:@"termId"];
            RegistrationTabBarController *tab = self.registrationTabController;
            [tab removeSearchedSection:section term:term];
        }
        if([[messages objectForKey:@"messages"] count] || [[messages objectForKey:@"failures"] count]) {
            NSPredicate *predicate = [NSPredicate predicateWithFormat:@"userEnteredPIN.length > 0"];
            NSArray *filteredArray = [self.registrationTabController.terms filteredArrayUsingPredicate:predicate];
            for(RegistrationTerm *term in filteredArray) {
                term.userEnteredPIN = nil;
            }
        }
    }
}


- (IBAction)startRegistration:(id)sender {
    
    UIActionSheet *actionSheet = [[UIActionSheet alloc] initWithTitle:nil
                                                             delegate:self
                                                    cancelButtonTitle:NSLocalizedString(@"Cancel", @"Cancel")
                                               destructiveButtonTitle:NSLocalizedString(@"Register", @"Register button")
                                                    otherButtonTitles:nil];
    
    
    if(UI_USER_INTERFACE_IDIOM() == UIUserInterfaceIdiomPad) {
        [actionSheet showFromBarButtonItem:self.registerButton animated:YES];
        } else {
        [actionSheet showFromToolbar:self.navigationController.toolbar];
    }
}

- (void)actionSheet:(UIActionSheet *)actionSheet didDismissWithButtonIndex:(NSInteger)buttonIndex {
    if (buttonIndex == actionSheet.destructiveButtonIndex) {
        [self sendEventWithCategory:kAnalyticsCategoryUI_Action withAction:kAnalyticsActionButton_Press withLabel:@"Register" withValue:nil forModuleNamed:self.module.name];
        [self registerSelectedCourses];
    }
}

-(void) updateStatusBar
{
    int count = 0;
    for(RegistrationTerm *term in self.self.registrationTabController.terms) {
        NSArray *plannedSections = [self.registrationTabController sectionsInCart:term.termId];
        for(RegistrationPlannedSection *course in plannedSections) {
            if(course.selectedInCart) {
                count++;
            }
        }
    }
    [self.navigationController setToolbarHidden:!(count>0) animated:YES];
    //self.registerButton.enabled = self.registrationAllowed;
    self.registerButton.title = [NSString stringWithFormat:NSLocalizedString(@"Register (%d)", @"label for register button in cart"), count];
}

-(BOOL)splitViewController:(UISplitViewController *)svc shouldHideViewController:(UIViewController *)vc inOrientation:(UIInterfaceOrientation)orientation
{
    return NO;
}

-(void) reloadData:(id)sender
{
    [self.tableView reloadData];
    [self updateBadge];
    
}

-(void) registerSelectedCourses
{
    MBProgressHUD *hud = [MBProgressHUD showHUDAddedTo: self.view animated:YES];
    hud.labelText = NSLocalizedString(@"Registering", @"loading message while waiting for registration");
    dispatch_time_t popTime = dispatch_time(DISPATCH_TIME_NOW, 0.01 * NSEC_PER_SEC);
    dispatch_after(popTime, dispatch_get_main_queue(), ^(void) {
        
        NSMutableArray *sectionRegistrations = [NSMutableArray new];
        
        for(RegistrationTerm *term in self.registrationTabController.terms) {
            NSArray *plannedSections = [self.registrationTabController sectionsInCart:term.termId];
            for(RegistrationPlannedSection *course in plannedSections) {

                if ( course.selectedInCart ) {
                    
                    NSMutableDictionary *sectionToRegister = [[NSMutableDictionary alloc] init];
                    
                    NSString * action = @"Add";
                    
                    if (course.isAudit) {
                        action = @"Audit";
                    } else if (course.isPassFail) {
                        action = @"PassFail";
                    }
                        
                    if(course.isVariableCredit) {
                        [sectionToRegister setObject:course.credits forKey:@"credits"];
                    }
                    if(term.requiresAltPin) {
                        [sectionToRegister setObject:term.userEnteredPIN forKey:@"altPin"];
                    }
                    [sectionToRegister setObject:term.termId forKey:@"termId"];
                    [sectionToRegister setObject:course.sectionId forKey:@"sectionId"];
                    [sectionToRegister setObject:action forKey:@"action"];

                    
                    [sectionRegistrations addObject:sectionToRegister];
                }
                
            }
        }
        
        NSDictionary *postDictionary = @{
                                         @"planId": self.planId,
                                         @"sectionRegistrations": sectionRegistrations,
                                         };
        NSError *jsonError;
        NSData * jsonData = [NSJSONSerialization dataWithJSONObject:postDictionary options:NSJSONWritingPrettyPrinted error:&jsonError];
        
        NSString *urlString = [NSString stringWithFormat:@"%@/%@/register-sections", [self.module propertyForKey:@"registration"], [[[CurrentUser sharedInstance] userid]  stringByAddingPercentEscapesUsingEncoding: NSUTF8StringEncoding]];
        NSString* planningTool = [self.module propertyForKey:@"planningTool"];
        if(planningTool) {
            urlString = [NSString stringWithFormat:@"%@?planningTool=%@", urlString, planningTool];
        }
        
        NSMutableURLRequest * urlRequest = [NSMutableURLRequest requestWithURL:[NSURL URLWithString:urlString]];
        //[urlRequest setValue:@"application/json" forHTTPHeaderField:@"Accept"];
        [urlRequest setValue:@"application/json" forHTTPHeaderField:@"Content-Type"];

        NSString *authenticationMode = [[NSUserDefaults standardUserDefaults] objectForKey:@"login-authenticationType"];
        if(!authenticationMode || [authenticationMode isEqualToString:@"native"]) {
            [urlRequest addAuthenticationHeader];
        }
        
        [urlRequest setHTTPMethod:@"PUT"];
        [urlRequest setHTTPBody:jsonData];
        NSError *error;
        NSURLResponse *response;
        
        NSDate *startDate = [NSDate date];
        NSData *responseData = [NSURLConnection sendSynchronousRequest:urlRequest returningResponse:&response error:&error];
        NSTimeInterval elapsedTimeInterval = [[NSDate date] timeIntervalSinceDate:startDate];
        [self sendUserTimingWithCategory:@"Registration" withTimeInterval:elapsedTimeInterval withName:@"Registration" withLabel:nil forModuleNamed:self.module.name];
        if(responseData) {
            NSDictionary *jsonResponse = [NSJSONSerialization JSONObjectWithData:responseData options:NSJSONReadingMutableContainers error:&error];
            [self.registrationTabController fetchRegistrationPlans:self];
            [self performSegueWithIdentifier:@"Register" sender:jsonResponse];
            [MBProgressHUD hideHUDForView:self.view animated:YES];
        } else {
            UIAlertView *alert = [[UIAlertView alloc] initWithTitle:NSLocalizedString(@"Error", @"Error") message:error.localizedDescription delegate:self cancelButtonTitle:NSLocalizedString(@"OK", @"OK") otherButtonTitles:nil];
            [alert show];
            [MBProgressHUD hideHUDForView:self.view animated:YES];
        }
    });
}

-(NSNumberFormatter *)creditsFormatter
{
    if(_creditsFormatter == nil) {
        _creditsFormatter = [NSNumberFormatter new];
        _creditsFormatter.numberStyle = NSNumberFormatterDecimalStyle;
        [_creditsFormatter setMinimumFractionDigits:1];
    }
    return _creditsFormatter;
}

- (void)tableView:(UITableView*)tableView commitEditingStyle:(UITableViewCellEditingStyle)editingStyle forRowAtIndexPath:(NSIndexPath*)indexPath
{
    if (editingStyle == UITableViewCellEditingStyleDelete)
    {
        RegistrationTerm *term = [self.registrationTabController.terms objectAtIndex:indexPath.section - 1];
        NSArray *plannedSections = [self.registrationTabController sectionsInCart:term.termId];
        RegistrationPlannedSection *plannedSection = [plannedSections objectAtIndex:indexPath.row];
        [self.registrationTabController removeFromCart:plannedSection];
        
        if (UI_USER_INTERFACE_IDIOM() == UIUserInterfaceIdiomPad) {
            UISplitViewController *split = [self.registrationTabController childViewControllers][0];
            UINavigationController *detailNavController = split.viewControllers[1];
            UIViewController *detailController = detailNavController.topViewController;
            if([detailController isKindOfClass:[RegistrationPlannedSectionDetailViewController class]]) {
                RegistrationPlannedSectionDetailViewController *registrationPlannedSectionDetailViewController = (RegistrationPlannedSectionDetailViewController *) detailController;
                if(registrationPlannedSectionDetailViewController.registrationPlannedSection == plannedSection) {
                    [registrationPlannedSectionDetailViewController clearView];
                }
            }
        }

        [self updateStatusBar];
    }
}

-(void) refreshTable:(id)sender
{
    [self.tableView reloadData];
    [self updateBadge];
    [self updateStatusBar];
}

-(void) promptForPIN:(NSString *)termId;
{
    UIAlertView * alert = [[UIAlertView alloc] initWithTitle:NSLocalizedString(@"PIN", @"PIN label for registration") message:NSLocalizedString(@"Enter the registration PIN", @"PIN label for registration") delegate:self cancelButtonTitle:NSLocalizedString(@"Cancel", @"Cancel") otherButtonTitles:NSLocalizedString(@"OK", @"OK"), nil];
    alert.tag = -2;
    alert.delegate = self;
    alert.alertViewStyle = UIAlertViewStylePlainTextInput;
    UITextField * alertTextField = [alert textFieldAtIndex:0];
    alertTextField.placeholder = NSLocalizedString(@"PIN", @"PIN label for registration");
    alertTextField.secureTextEntry = YES;
    self.termNeedingPIN = termId;
    [alert show];
}

- (void)alertView:(UIAlertView *)alertView clickedButtonAtIndex:(NSInteger)buttonIndex
{
    if(alertView.tag == -2) {
        if(buttonIndex == 0) {
            NSArray *sectionsInCartForTerm = [self.registrationTabController sectionsInCart:self.termNeedingPIN];
            for(RegistrationPlannedSection *section in sectionsInCartForTerm) {
                section.selectedInCart = NO;
            }
            [self refreshTable:self];
        }
        if(buttonIndex == 1) {
            RegistrationTerm *term = [self.registrationTabController findTermById:self.termNeedingPIN];
            self.termNeedingPIN = nil;
            term.userEnteredPIN = [[alertView textFieldAtIndex:0] text];
        }
    }
}

- (BOOL)alertViewShouldEnableFirstOtherButton:(UIAlertView *)alertView
{
    if(alertView.tag == -2) {
        NSString *text = [[alertView textFieldAtIndex:0] text];
        return ([text length] > 0);
    }
    return YES;
    
}


@end
